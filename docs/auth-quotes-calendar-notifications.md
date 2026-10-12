# Auth, quotes, calendar and notifications — backend implementation notes

Implements the handoff contract *auth-quotes-calendar-notifications* (sign-in, self-registration and review, quote
lifecycle, orders calendar, notifications over in-app/SSE/Web Push, transactional email, domain events with an outbox).
This page describes what the backend does, where it lives and where it deliberately differs from the contract.
Architecture decisions are summarised in `context.md` (section *Domain events and the outbox*).

## Modules

| Package | Responsibility |
|---|---|
| `eventing` | `DomainEvent`, `@EventType`, `DomainEventPublisher` (outbox), relay, dispatcher, retries, event trail, `ClusterJobRunner` |
| `mail` | Template catalog (`mail/catalog.json`, es-MX/en), Thymeleaf renderer, `Mailer` (enqueue only), `MailDispatcher` (SMTP) |
| `identity` | Sign-in by username or email, lockout, 2FA challenge, self-registration and its review, recovery, verification, screen lock |
| `quote` + `application/service/quote` | Quote state machine, history, lifecycle commands, public accept/reject, jobs |
| `calendar` | Orders calendar, recipes, status flow, reminders, summary, `.ics` |
| `notification` | Inbox, preferences, SSE stream, Web Push (RFC 8291/8292), projections of domain events |

## Side effects are events

Every side effect leaves the request through `outbox_messages`, written in the same transaction as the business change.
The relay is woken after each commit, claims messages with `FOR UPDATE SKIP LOCKED` (safe on every instance), keeps the
order of one aggregate, retries with per-type back-off (`DEFAULT`, `MAIL`, `PUSH`) and dead-letters after the budget.
Each consumer records `processed_events` in its own transaction, so it applies a message at most once.

What runs inside the request and what does not (§8.5):

| Request | Synchronous | Outbox consumers |
|---|---|---|
| `POST /auth/register` | account + registration rows | receipt and verification emails, admin email and in-app/push, kitchen defaults |
| `POST /auth/login` (failure) | attempt row, counters, lock decision | lock email, security notification |
| `POST /quotes/{id}/accept` | transition, history, calendar entry + recipes | client emails, reminders, quote link, notifications, SSE hints |
| `POST /public/quotes/{t}/accept` | transition, history | baker email, in-app, push, SSE |
| `POST /calendar/events` | entry + recipes | reminders, client email, quote link, SSE hint |
| `POST /notifications/read` | read flags | `read-state` frame to the user's other tabs |
| Profile, fiscal data, catalogs | the change | audit row (was an `@Async` listener), avatar/file object deletion |

### Event catalog and consumers

| Event | Consumers |
|---|---|
| `identity.account-locked` | `AccountLockedMail`, `NotifyAccountLocked` |
| `identity.new-device-sign-in` | `NewDeviceMail`, `NotifyNewSignIn` |
| `identity.password-changed` | `PasswordChangedMail`, `NotifyPasswordChanged` |
| `identity.session-revoked`, `identity.session-locked` | `CloseStreamsOn*` (ends the SSE streams of the session) |
| `identity.registration-attempt-on-existing-email` | `RegistrationAttemptMail` |
| `identity.account-created` | `KitchenFixedCostSeeding` |
| `identity.user-signed-in`, `account-unlocked`, `session-unlocked`, `email-verified` | trail only |
| `registration.submitted` | applicant and reviewer emails, `NotifyRegistrationSubmitted`, SSE hint to reviewers |
| `registration.approved` / `rejected` / `expired` | applicant emails, `NotifyRegistrationApproved`, SSE hints |
| `quote.sent` (EMAIL channel) | `QuoteToClientMail` |
| `quote.viewed` (coalesced per 6 h) | `NotifyQuoteViewed` |
| `quote.accepted` | `AcceptedConfirmationMail`, `ClientAcceptedMail` (client actor), `NotifyQuoteAccepted` (client actor) |
| `quote.rejected` | `NotifyQuoteRejected` (client actor) |
| `quote.expiring` (once per quote) | `QuoteExpiringMail`, `NotifyQuoteExpiring` |
| every `quote.*` | SSE `domain` hint to the owner |
| `calendar.event.*` | reminder projection, quote link/unlink, order notifications, SSE hints |
| `calendar.order-scheduled` | `OrderScheduledClientMail`, `NotifyOrderScheduled` |
| `calendar.reminder-due` / `calendar.production-due` | `CalendarReminderMail`, `NotifyEventReminder` / `NotifyProductionDue` |
| `notification.created` | `StreamNotification` (SSE), `PushNotification` (Web Push, `PUSH` retries) |
| `notification.read` | `StreamReadState` |
| `push.delivery-failed` | trail only |
| `mail.requested` | `MailDispatcher` |
| `audit.entry-recorded` | `AuditEntryWriter` |
| `account.avatar-released`, `kitchen.recipe-files-released` | storage object cleanup |
| `recipe.shared` | `RecipeShareMailHandler` |

Payload components holding secrets or personal data are `@Sensitive`: encrypted at rest in the outbox and removed from
the event trail (`GET /admin/events/{id}/trail`).

### Scheduled jobs (advisory lock per job, `cronos.job.duration{job}` / `cronos.job.items{job}`)

| Job | Cadence (tenant time) | Property |
|---|---|---|
| `QuoteExpiryJob` | every 15 min | `app.quotes.expiry-cron` |
| `QuoteExpiringJob` | daily 09:00 | `app.quotes.expiring-cron` |
| `CalendarReminderJob` | every minute | `app.calendar.reminder-cron` |
| `RegistrationExpiryJob` | daily 03:00 | `app.auth.registration-expiry-cron` |
| `TokenCleanupJob` | hourly | `app.auth.token-cleanup-cron` (expired links, 2FA challenges, SSE tickets) |
| `NotificationRetentionJob` | daily 04:00 | `app.notifications.retention-cron` |
| `OutboxCleanupJob` | daily 05:00 | `app.eventing.cleanup-cron`, keeps `app.eventing.dispatched-retention` |

## Configuration

| Key | Default | Notes |
|---|---|---|
| `app.notifications.push-enabled` | `false` | Push is active only with both VAPID keys |
| `app.notifications.vapid-public-key` / `vapid-private-key` | — | base64url P-256 (65-byte point / 32-byte scalar); private key from the secret manager only |
| `app.notifications.push-subject` | `mailto:soporte@cronos.mx` | VAPID `sub` |
| `app.notifications.push-allowed-hosts` | FCM, Mozilla, WNS, Apple | endpoints elsewhere are refused (no server-side requests to arbitrary URLs) |
| `app.notifications.stream-channel` | `cronos:sse` | Redis pub/sub channel of the stream fan-out |
| `app.notifications.stream-timeout` / `heartbeat` / `ticket-ttl` | 30 m / 25 s / 60 s | |
| `app.eventing.*` | | relay poll interval, batch, concurrency, lease, retention |
| `app.mail.*` | | sender, brand, support address, unsubscribe URL |

Generate VAPID keys once (`npx web-push generate-vapid-keys`) and store them with the other secrets (see
`docs/RUNBOOK.md`). SPF, DKIM and DMARC for the sending domain are deployment prerequisites.

## Endpoints

| Area | Endpoints |
|---|---|
| Auth | `POST /auth/login`, `/auth/login/two-factor`, `/auth/logout`, `/auth/register`, `/auth/email/verify`, `/auth/email/verify/resend`, `/auth/password/forgot`, `GET /auth/password/reset/{token}`, `POST /auth/password/reset`, `/auth/lock`, `/auth/unlock`, `GET /auth/password-policy`, `/auth/availability` |
| Registrations | `GET /iam/registrations`, `/iam/registrations/stats`, `/iam/registrations/{id}`, `POST /{id}/approve`, `/{id}/reject`, `/{id}/resend-verification`, `/bulk` |
| Quotes | `GET /quotes` (`search`, `statuses`, `sort`), `/quotes/stats`, `POST /quotes/{id}/shared`, `/send-email`, `/accept`, `/reject`, `/revoke`, `/duplicate`, `DELETE /quotes/{id}` |
| Public quotes | `GET /public/quotes/{token}`, `POST /public/quotes/{token}/accept`, `/reject` |
| Calendar | `GET|POST /calendar/events`, `GET /calendar/events/upcoming`, `GET|PUT|DELETE /calendar/events/{id}`, `PATCH /{id}/schedule`, `POST /{id}/status`, `GET /calendar/summary`, `/calendar/export.ics` |
| Notifications | `GET /notifications`, `/unread-count`, `POST /read`, `/read-all`, `DELETE /{id}`, `GET|PUT /settings`, `POST /stream/ticket`, `GET /stream?ticket=`, `GET /push/config`, `GET|POST /push/subscriptions`, `POST /push/subscriptions/unsubscribe`, `DELETE /push/subscriptions/{id}`, `POST /push/subscriptions/renew` (public), `POST /push/test` |
| Email catalog | `GET /admin/email-templates`, `POST /admin/email-templates/{key}/preview`, `/test` |
| Event trail | `GET /admin/events`, `/admin/events/{id}/trail`, `POST /admin/events/{id}/retry` |

## Deviations from the contract

| Contract | Implementation | Why |
|---|---|---|
| ShedLock | PostgreSQL advisory locks (`ClusterJobRunner`) | already used by the existing jobs; no new table or dependency |
| `*_normalized` columns, `password_reset_tokens`, `email_verification_tokens` | `lower(username)` index, `email_blind_index`, the existing hashed `user_tokens` | email is encrypted at rest; one token store |
| Separate `locked_until`, attempts tables | `users.status_until`, `login_history` | existing columns kept |
| Bucket4j backed by Redis | in-memory Bucket4j per instance | limits are per node (documented in `AuthRateLimiter`) |
| OpenTelemetry trace id | W3C `traceparent` parsed by `TraceIdFilter` | no OTel dependency yet |
| Quote search by SQL | in-memory after decryption (bounded by the owner's quotes) | client fields are encrypted |
| Calendar GiST index | two btree indexes | no `btree_gist` extension |
| Calendar client fields, notification texts in clear | encrypted at rest | same policy as quote client fields |
| `QUOTE.QUOTE.MANAGE` | existing `QUOTE.QUOTE.CREATE/UPDATE/SHARE/DELETE` | fine-grained permissions already granted to roles |
| `mail.sent` event | `mail_messages` row per attempt | the trail already shows deliveries |
| `nl.martijndwars:web-push` | JDK crypto (`WebPushCrypto`) | no Bouncy Castle; RFC 8291 verified by a decrypting test |
| `cronos.web-push.*` | `app.notifications.*` | the project's property prefix |
| `user_accounts` | `users` | table name of this schema |
| `NOTIFICATION.BROADCAST.SEND` endpoint | permission seeded, no endpoint | reserved by the contract |

The UI copy of the email catalog (`src/assets/email-templates/catalog.json` in `cronos-ui`) must be kept identical to
`src/main/resources/mail/catalog.json`; `scripts/mail/build_catalog.py` regenerates the backend copy.
