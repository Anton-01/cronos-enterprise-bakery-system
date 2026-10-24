# Email Template Customization — Backend API Contract

Frontend: `src/app/pages/cronos/admin/email-templates/` (Angular 21 + PrimeNG 21).
TypeScript mirror of every shape below: `src/app/core/models/email-template.models.ts`.
Client: `src/app/core/services/notifications/email-template.service.ts`. **Change the model and this doc together.**

Builds on `auth-quotes-calendar-notifications.md` §7 (catalog, rendering, sending). That section stays valid; this doc adds editing (content per locale, brand, sender, images) and caching, and fixes the response shape.

---

## 0. Agent brief

Administrators can now edit every transactional email from the UI:

- **Content per template and locale:** subject, preview text (preheader) and the ordered list of blocks, including a new `image` block.
- **Brand & sender shared by all emails:** From name and address, Reply-To, logo, an optional header banner, colors, brand name, and footer data (support email, address, preferences link).
- **Images** are uploaded to the backend and referenced by a public URL.

Built-in templates stay in the classpath (`mail/catalog.json`) as the **read-only defaults**. Edits are stored as **overrides** in the database. The effective template is `override ?? default` for each `(key, locale)`, so "Restore default" just deletes the override.

The rendering path (`MailDispatcher`) and the admin catalog read the same effective data. Both go through a cache that is evicted after commit (§5).

### Non-negotiables (same standards as the other docs)

- Java 21 + Spring Boot 3, "Big Four" quality. **Never `@Autowired`**: constructor injection only, enforced with ArchUnit.
- English Javadoc on every public type. Records for DTOs and value objects.
- Advanced Collections usage: `EnumMap<EmailLocale, …>`, `EnumSet`, `Collectors.groupingBy` with downstream collectors, `List.copyOf` / `Map.copyOf` for immutability.
- Side effects are **domain events through the transactional outbox**. Consumers must be idempotent. Every event carries `correlationId`, `causationId` and `traceId`.
- Optimistic locking on every write (`revision`). Mismatch → `409 STALE_REVISION`.
- Every write lands in the IAM audit log (actor, before/after diff, trace id).

---

## 1. Fix the current `GET /admin/email-templates` response first

The UI currently tolerates the response below through `normalizeCatalog`. It must still be fixed so that adapter can be deleted.

| Field | Served on 2026-10-24 | Contract |
|---|---|---|
| `data.version` | `1` (number) | `string`, e.g. `"2026-10-24"` |
| `data.brand` | missing | `EmailBrand` (§3.2) |
| `data.sender` | missing | `EmailSender` (§3.2) |
| `templates[].category` | `"auth"`, `"admin"`, `"quote"`, `"calendar"`, `"recipe"` | enum `ACCOUNT \| SECURITY \| ADMIN \| QUOTES \| ORDERS \| RECIPES` (mapping below) |
| Locale keys in `content` and in every `description` | `"es"`, `"en"` | `"es-MX"`, `"en"` (same values as `Accept-Language`) |
| `templates[].description` | English string | `{ "es-MX": string, "en": string }` |
| `blocks[]` | every block carries `text, label, url, tone, items, rows` with `null` / `[]` / `"info"` defaults | only the fields of its type (§3.3) |

Category mapping (the UI groups the list by category, so `auth` must be split in two):

| Category | Keys |
|---|---|
| `ACCOUNT` | `auth.registration-received`, `auth.email-verification`, `auth.account-approved`, `auth.account-rejected`, `auth.invitation` |
| `SECURITY` | `auth.password-reset`, `auth.password-changed`, `auth.account-locked`, `auth.new-sign-in`, `auth.temporary-password`, `auth.email-changed`, `auth.two-factor-disabled`, `auth.recovery-code-used`, `auth.registration-attempt` |
| `ADMIN` | `admin.registration-pending` |
| `QUOTES` | `quote.sent-to-client`, `quote.accepted-baker`, `quote.accepted-client`, `quote.expiring-reminder` |
| `ORDERS` | `calendar.order-reminder`, `calendar.order-scheduled-client` |
| `RECIPES` | `recipe.shared` |

Store the category in `mail/catalog.json` as the enum value. Never derive it from the key at runtime.

---

## 2. Conventions

| Item | Value |
|---|---|
| Base path | `/api/v1/admin/email-templates` |
| Auth | `Authorization: Bearer <access JWT>`; tenant from the JWT |
| Envelope | `{ "meta": { "traceId", "timestamp" }, "status": "SUCCESS" \| "ERROR", "message", "data", "errors": [ { "code", "field", "message" } ] }` |
| Locale path segment | `es-MX` \| `en` (`EmailLocale` enum, case-sensitive; anything else → 400) |
| `errors[].field` | request-body path (`blocks[3].url`, `brand.primaryColor`, `sender.fromAddress`). The UI shows `message` for it |

---

## 3. Shapes

### 3.1 `EmailTemplate` (catalog item, effective content)

```json
{
  "key": "auth.password-reset",
  "category": "SECURITY",
  "audience": "USER",
  "trigger": "PasswordResetRequestedEvent",
  "transactional": true,
  "description": { "es-MX": "Enlace de un solo uso para recuperar la contraseña.", "en": "Single-use password recovery link." },
  "variables": [
    { "name": "resetUrl", "description": { "es-MX": "Enlace de restablecimiento", "en": "Reset link" }, "sample": "https://app.cronos.mx/auth/reset-password?token=sample", "kind": "url" }
  ],
  "content": {
    "es-MX": { "revision": 3, "subject": "Restablece tu contraseña", "preheader": "El enlace vence pronto.", "blocks": [ /* §3.3 */ ] },
    "en":    { "revision": null, "subject": "Reset your password", "preheader": "The link expires soon.", "blocks": [] }
  },
  "customizedLocales": ["es-MX"],
  "updatedAt": "2026-10-24T19:30:00Z",
  "updatedBy": "Antón Admin"
}
```

- `content[locale].revision`: the override's revision, or `null` when the locale serves the built-in default.
- `customizedLocales`: the locales that have an override (used for the "Customized" badge and to show "Restore default").
- `updatedAt` / `updatedBy`: from the most recent override of any locale, or `null`.
- `variables` are **not editable**. They are the contract between the domain event and the template.

### 3.2 `EmailBrand`, `EmailSender`, `EmailSettings`

```json
{
  "brand": {
    "name": "Cronos",
    "primaryColor": "#10b981",
    "accentColor": "#f9a94c",
    "logoUrl": "https://assets.cronos.mx/mail/3f1c…e9.png",
    "headerImageUrl": null,
    "supportEmail": "soporte@cronos.mx",
    "address": "Ciudad de México, México",
    "preferencesUrl": "https://app.cronos.mx/cronos/notificaciones?tab=preferences"
  },
  "sender": { "fromName": "Cronos", "fromAddress": "no-reply@cronos.mx", "replyTo": "soporte@cronos.mx" },
  "revision": 7
}
```

The catalog response embeds `brand`, `sender` and `settingsRevision` (the same value as `revision` here). Defaults come from `MailProperties` until the first save.

### 3.3 Blocks — polymorphic, one record per type

Model them as `sealed interface EmailBlock permits Heading, Paragraph, Button, Code, Details, Callout, ListBlock, Image, Divider, Signature`, with Jackson `@JsonTypeInfo(use = NAME, property = "type")` + `@JsonSubTypes`. That is what removes the `null` / `[]` noise.

| `type` | Fields | Rules |
|---|---|---|
| `heading` | `text` | 1–200 chars |
| `paragraph` | `text` | 1–2000 chars |
| `button` | `label`, `url` | label 1–60; url = `https://…`, `mailto:…`, or exactly one `{{ var }}` of `kind: url` |
| `code` | `text`, `caption?` | text 1–64, caption ≤ 120 |
| `details` | `rows: [{ label, value }]` | 1–12 rows, label ≤ 60, value ≤ 300 |
| `callout` | `tone`, `text` | tone ∈ `info, success, warning, danger`; text 1–600 |
| `list` | `items: string[]` | 1–20 items, each ≤ 300 |
| `image` **(new)** | `url`, `alt`, `width?`, `href?` | url `https://` only (in prod, must be the asset host, §4.6); alt ≤ 200 (required for accessibility, may be empty only for decorative images); width 40–536 px or null (= full width); href like `button.url` |
| `divider` | — | |
| `signature` | `text` | 1–200 |

Content-level rules: subject 1–150 and preheader 0–200 chars; 1–40 blocks.

**Every `{{ name }}` placeholder in subject, preheader or blocks must be a declared variable of that template or an implicit one (`brandName`, `supportEmail`).** Otherwise → `400 UNKNOWN_VARIABLE`, with `field` pointing at the offending path. This keeps the §7.1 guarantee ("the sender supplies every declared variable") true after edits.

---

## 4. Endpoints

| Method | Path | Permission | Body → Response |
|---|---|---|---|
| GET | `/` | `NOTIFICATION.EMAIL_TEMPLATE.READ` | → `EmailTemplateCatalog` (`version, brand, sender, settingsRevision, templates[]`). ETag (§5.3) |
| PUT | `/{key}/content/{locale}` | `NOTIFICATION.EMAIL_TEMPLATE.MANAGE` **(new)** | `EmailTemplateContent` (`revision, subject, preheader, blocks`) → `EmailTemplate` |
| DELETE | `/{key}/content/{locale}` | MANAGE | → `EmailTemplate` (back to defaults for that locale) |
| GET | `/{key}/content/{locale}/history?page&size` | READ | → page of `{ revision, subject, preheader, blocks, changedAt, changedBy, action: SAVED \| RESET }` |
| PUT | `/settings` | MANAGE | `EmailSettings` → `EmailSettings` |
| POST | `/assets` | MANAGE | multipart `file` → `EmailAsset` |
| POST | `/{key}/preview` | READ | `{ locale, variables, content?: EmailTemplateContent, settings?: EmailSettings }` → `{ subject, preheader, html, text }` |
| POST | `/{key}/test` | `NOTIFICATION.EMAIL_TEMPLATE.TEST_SEND` | `{ locale, to?, variables, content?: EmailTemplateContent }` → `{ messageId }` |

### 4.1 `PUT /{key}/content/{locale}`

- Body `revision`: the revision the editor started from (`null` when it started from the default).
  - `null` with no existing override → insert with revision 1.
  - `null` but an override exists → `409 STALE_REVISION`.
  - Not null → `UPDATE … WHERE revision = :revision`. 0 rows → `409 STALE_REVISION`.
- Validate with §3.3. Unknown key → `404 EMAIL_TEMPLATE_NOT_FOUND`.
- If the content is identical to the default, delete the override instead of storing a copy (keeps `customizedLocales` honest).
- Writes a history row and the outbox event `EmailTemplateCustomizedEvent` (§6) in the same transaction.

### 4.2 `DELETE /{key}/content/{locale}`

Idempotent: deleting a missing override returns the template with `200`. Writes a history row (`RESET`) and `EmailTemplateResetEvent`.

### 4.3 `PUT /settings`

Same revision rules (singleton row per tenant). Validation:

- Colors: `^#[0-9a-fA-F]{6}$`.
- Emails: RFC 5322.
- URLs: `https://`.
- `logoUrl` / `headerImageUrl`: asset host in prod.
- **`sender.fromAddress` must belong to a domain in `mail.verified-domains` (SPF/DKIM aligned with the provider). Otherwise → `400 SENDER_DOMAIN_NOT_VERIFIED`.** Accepting an unverified From silently sends everything to spam.

### 4.4 `POST /{key}/preview` and `/test` with a draft

When `content` is present, render it instead of the stored content, after the **same validation** as §4.1 (a test must not bypass the `UNKNOWN_VARIABLE` / URL rules). Nothing is persisted. `/test` keeps the existing `[TEST]` subject prefix and the 20/hour/user rate limit.

### 4.5 `POST /assets`

| Rule | Value |
|---|---|
| Types | PNG, JPEG, GIF, WebP, detected by **magic bytes**, not by extension or the client's `Content-Type`. SVG is rejected (most mail clients don't render it; it's also an XSS vector) |
| Size | ≤ 2 MB, ≤ 4000 × 4000 px |
| Processing | Strip EXIF/metadata. Re-encode JPEG/PNG/WebP (drops polyglot payloads). Keep the GIF animation |
| Storage key | `mail/{sha256}.{ext}`, content-addressed. Re-uploading the same file returns the existing asset (dedupe) |
| Response | `{ id, url, contentType, sizeBytes, width, height }`; `url` is absolute, public, and never expires (emails sit in inboxes for years) |
| Errors | `400 ASSET_INVALID_TYPE`, `400 ASSET_TOO_LARGE`, `400 ASSET_DIMENSIONS` |

### 4.6 Asset serving

Serve from object storage (S3-compatible; local filesystem in dev) behind `mail.assets.public-base-url` (e.g. `https://assets.cronos.mx`), with `Cache-Control: public, max-age=31536000, immutable`. URLs must be absolute and **must not require auth or cookies**: mail clients and Gmail's image proxy fetch them anonymously. Never delete an asset that a saved override or the settings referenced: old emails still point to it. A cleanup job may delete assets that were never referenced and are older than 30 days.

---

## 5. Cache (required)

The effective template is read on **every outgoing email** and on every catalog load, while writes are rare (a few per month). Cache aggressively and evict precisely.

### 5.1 Application cache

Use Spring Cache with Caffeine on each instance. If more than one instance runs, add a Redis-backed L2 cache, or broadcast evictions (§5.2).

| Cache | Key | Value | Size / TTL |
|---|---|---|---|
| `email.defaults` | — | parsed `mail/catalog.json` (`Map<String, TemplateDefinition>`) | loaded once at startup, never evicted (classpath) |
| `email.effective` | `(tenantId, key, locale)` | `EffectiveContent` (override or default, already validated) | max 2 000, `expireAfterWrite` 30 min as a safety net |
| `email.settings` | `tenantId` | `EmailSettings` | max 500, 30 min |
| `email.catalog` | `(tenantId, acceptLanguage)` | the full catalog DTO + its ETag | max 500, 30 min |
| `email.compiled` | `(tenantId, key, locale, contentRevision, settingsRevision)` | pre-parsed Thymeleaf fragment tree / render plan | max 2 000, 30 min |

- The `MailDispatcher` render path reads `email.effective` + `email.settings` + `email.compiled`. It must not hit the database for a cached template.
- Expose hit/miss/eviction metrics (`CaffeineCacheMetrics` → Micrometer) and alert if the `email.effective` hit ratio drops below 95 %.

### 5.2 Eviction — after commit, everywhere

- Evict in a `@TransactionalEventListener(phase = AFTER_COMMIT)`, **never before commit** (a concurrent read would re-cache the old row):
  - Content save/reset evicts `email.effective(tenant, key, locale)`, `email.catalog(tenant, *)` and `email.compiled(tenant, key, locale, *)`.
  - A settings save evicts `email.settings(tenant)`, `email.catalog(tenant, *)` and `email.compiled(tenant, *)`.
- Multi-instance: the outbox events of §6 are also consumed by an `EmailCacheInvalidator` on **every** instance (fan-out, e.g. Redis pub/sub or a broadcast topic), which applies the same evictions. Idempotent by `eventId`.
- The UI also invalidates its own in-memory catalog after every write (`EmailTemplateService.invalidate()`).

### 5.3 HTTP caching of `GET /`

- `ETag: "W/<sha256(defaultsVersion | settingsRevision | max(override.revision) | count(override) | acceptLanguage)>"`, computed from the cached catalog (no extra query).
- `Cache-Control: private, no-cache` (always revalidate; the response depends on the tenant and permissions).
- `If-None-Match` → `304 Not Modified` with no body.
- Assets: see §4.6 (immutable, one year).

---

## 6. Domain events (transactional outbox)

| Event | When | Payload (plus `eventId, occurredAt, tenantId, actorId, correlationId, causationId, traceId`) |
|---|---|---|
| `EmailTemplateCustomizedEvent` | content saved | `templateKey, locale, revision, changedFields` |
| `EmailTemplateResetEvent` | override deleted | `templateKey, locale, previousRevision` |
| `EmailSettingsChangedEvent` | settings saved | `revision, changedFields` (`sender.fromAddress`, `brand.logoUrl`, …) |
| `EmailAssetUploadedEvent` | asset stored (not deduped) | `assetId, sha256, contentType, sizeBytes` |

Consumers:

- `EmailCacheInvalidator`: on every instance (§5.2).
- `AuditLogProjector`: writes the before/after diff to the IAM audit log.
- `SecurityNotifier`: when `sender.fromAddress` or `replyTo` changes, notify the other administrators (it's a phishing-relevant change).

---

## 7. Database (Flyway)

```sql
-- V__email_template_customization.sql
CREATE TABLE email_settings (
    tenant_id          UUID PRIMARY KEY,
    brand_name         VARCHAR(80)  NOT NULL,
    primary_color      CHAR(7)      NOT NULL,
    accent_color       CHAR(7)      NOT NULL,
    logo_url           VARCHAR(500) NOT NULL,
    header_image_url   VARCHAR(500),
    support_email      VARCHAR(254) NOT NULL,
    address            VARCHAR(200) NOT NULL,
    preferences_url    VARCHAR(500) NOT NULL,
    from_name          VARCHAR(80)  NOT NULL,
    from_address       VARCHAR(254) NOT NULL,
    reply_to           VARCHAR(254),
    revision           BIGINT       NOT NULL DEFAULT 1,
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by         UUID         NOT NULL
);

CREATE TABLE email_template_override (
    id            UUID PRIMARY KEY,
    tenant_id     UUID         NOT NULL,
    template_key  VARCHAR(80)  NOT NULL,
    locale        VARCHAR(5)   NOT NULL CHECK (locale IN ('es-MX', 'en')),
    subject       VARCHAR(150) NOT NULL,
    preheader     VARCHAR(200) NOT NULL,
    blocks        JSONB        NOT NULL,          -- validated List<EmailBlock>, written by Jackson
    revision      BIGINT       NOT NULL DEFAULT 1,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by    UUID         NOT NULL,
    CONSTRAINT uq_email_override UNIQUE (tenant_id, template_key, locale)
);

CREATE TABLE email_template_override_history (
    id            UUID PRIMARY KEY,
    tenant_id     UUID         NOT NULL,
    template_key  VARCHAR(80)  NOT NULL,
    locale        VARCHAR(5)   NOT NULL,
    revision      BIGINT       NOT NULL,
    action        VARCHAR(10)  NOT NULL CHECK (action IN ('SAVED', 'RESET')),
    subject       VARCHAR(150),
    preheader     VARCHAR(200),
    blocks        JSONB,
    changed_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    changed_by    UUID         NOT NULL,
    trace_id      VARCHAR(64)
);
CREATE INDEX ix_email_history_lookup ON email_template_override_history (tenant_id, template_key, locale, changed_at DESC);

CREATE TABLE email_asset (
    id            UUID PRIMARY KEY,
    tenant_id     UUID         NOT NULL,
    sha256        CHAR(64)     NOT NULL,
    storage_key   VARCHAR(200) NOT NULL,
    content_type  VARCHAR(40)  NOT NULL,
    size_bytes    INTEGER      NOT NULL,
    width         INTEGER,
    height        INTEGER,
    referenced    BOOLEAN      NOT NULL DEFAULT FALSE,   -- set when a save points to it; cleanup job skips TRUE
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by    UUID         NOT NULL,
    CONSTRAINT uq_email_asset UNIQUE (tenant_id, sha256)
);
```

The permission seed adds `NOTIFICATION.EMAIL_TEMPLATE.MANAGE` to `ADMIN` (and implicitly `SUPER_ADMIN`). The UI's legacy role map already includes it.

---

## 8. Rendering changes (`auth` doc §7.2)

- **Effective content:** `EmailTemplateRenderer.render(key, locale, variables)` reads the effective content and settings from the cache, never `mail/catalog.json` directly.
- **New `image` fragment:** port the markup from `email-renderer.ts` (`case 'image'`) verbatim: centered `<img>` with `width` attribute, `max-width`, `height:auto` and `border-radius:10px`, wrapped in `<a>` when `href` is set. The plain-text part renders `[alt]`, plus `(href)` when there is one.
- **Header banner:** when `brand.headerImageUrl` is set, render a 600 px full-width image row above the body and drop the 4 px `primaryColor` top border (same as the UI).
- **Envelope:**
  - `From: "{fromName}" <{fromAddress}>`.
  - `Reply-To` from settings when present.
  - `List-Unsubscribe` (with `preferencesUrl`) on non-transactional emails.
- The `brandName` and `supportEmail` implicit variables come from the settings (not from `MailProperties`) once a settings row exists.

---

## 9. Suggested structure

```
notification/email/
  domain/        EmailLocale, EmailCategory, EmailBlock (sealed) + records, EffectiveContent, EmailSettings, TemplateDefinition
  application/   EmailTemplateQueryService, EmailTemplateCommandService, EmailSettingsService, EmailAssetService,
                 ContentValidator (variables, URLs, limits), EmailCacheInvalidator
  infrastructure/ JpaEmailTemplateOverrideRepository, JpaEmailSettingsRepository, S3EmailAssetStorage / LocalEmailAssetStorage,
                 ClasspathTemplateDefaults (loads mail/catalog.json once)
  web/           EmailTemplateController, EmailAssetController, DTO records, ETag support
```

- Validation errors are collected (not thrown on the first one) into `List<FieldError>`, so the UI can mark every bad block in one round-trip.
- `ContentValidator` extracts placeholders with one precompiled `Pattern` and checks them against an `EnumSet`/`Set.copyOf` of the declared variable names.

---

## 10. Tests (Definition of Done)

- [ ] Contract test: `GET /` deserializes into the shape of §3, with the enum categories, `es-MX` keys, `brand`, `sender`, and no non-type fields in blocks.
- [ ] Every built-in template renders with its samples in both locales with no `{{` left (existing test, now over effective content).
- [ ] `PUT` content: happy path; `STALE_REVISION` (both the `null` and the numeric case); `UNKNOWN_VARIABLE` with the right `field`; `javascript:` URL rejected; image with a non-https URL rejected; saving content equal to the default deletes the override.
- [ ] `DELETE` is idempotent and serves the default again.
- [ ] `PUT /settings`: unverified From domain → `SENDER_DOMAIN_NOT_VERIFIED`; bad hex color → field error.
- [ ] Assets: SVG and a PNG-renamed HTML file are rejected by magic bytes; EXIF is stripped; uploading the same file twice returns the same `id`.
- [ ] Cache:
  - A second render of the same template runs 0 SQL statements (assert with a datasource-proxy counter).
  - After a save, the next render uses the new content on every instance (Testcontainers with two app contexts).
  - Eviction happens only after commit (a rolled-back save keeps the old cached content).
- [ ] `GET /` returns an ETag and `304` on `If-None-Match`; the ETag changes after any content or settings save.
- [ ] Outbox events are written in the same transaction as the change; the consumers are idempotent (replaying the same event twice changes nothing).
- [ ] ArchUnit: no field injection, no `@Autowired`; `web` doesn't depend on `infrastructure`.

---

## 11. What the UI already does (for context)

- Normalizes the current response (`normalizeCatalog` / `normalizeTemplate` / `normalizeBlock`), covered by `email-template.service.spec.ts`. It will be reduced to a pass-through once §1 ships.
- **Editor:** subject, preheader, add/move/remove blocks of every type, per-type fields, image upload (client-side checks: PNG/JPEG/GIF/WebP, ≤ 2 MB). Drafts live per `(key, locale)` and the preview renders the draft.
- **Saving:** "Save" sends `PUT …/content/{locale}` with the `revision` it started from, and "Restore default" sends `DELETE` (two-click confirm). "Send test" sends the unsaved draft in `content`.
- **Brand & sender tab:** sender, logo, header banner, colors, brand name and footer; saves with `PUT /settings`.
- **Full-size preview dialog:** ES/EN and desktop/mobile switches.
- Editing is gated by `NOTIFICATION.EMAIL_TEMPLATE.MANAGE`; without it the page stays read-only (variables tab only). Saving and uploading are disabled while the catalog comes from the bundled seed (offline).

---

## 12. Backend implementation notes (2026-10-24)

Where the code lives and where it deliberately differs from the text above.

| Topic | Implementation |
|---|---|
| Packages | `mail.domain` (`EmailLocale`, `EmailCategory`, sealed `EmailBlock`, `TemplateDefinition`, `TemplateDefaults`, `EffectiveContent`, `EmailSettings`, `EmailTemplateEvents`), `mail.application` (`EmailTemplateQueryService`, `EmailTemplateCommandService`, `EmailSettingsService`, `EmailAssetService`, `ContentValidator`, `EmailContentProvider`), `mail.infrastructure` (JDBC repositories, `ClasspathTemplateDefaults`, `EmailImageSanitizer`, storage adapters, `EmailCacheInvalidator`), `mail.api` (controller, DTO records). `notification.infrastructure.EmailSecurityNotifier` is the `SecurityNotifier`. |
| Tenant | The JWT carries no tenant claim and the deployment is single-tenant: every request resolves to `app.mail.tenant-id` (default the nil UUID). All tables, cache keys and events still carry `tenantId`. |
| Configuration | Under the existing `app.mail.*` prefix: `app.mail.verified-domains` (the `sender-address` domain is always included), `app.mail.assets.{storage, public-base-url, local-directory, gcs-bucket, enforce-host}`, `app.mail.brand.*` defaults, `app.mail.cache-channel`. Prod turns `enforce-host` on. |
| Repositories | `*CustomRepository` on `NamedParameterJdbcTemplate`, like the rest of the outbox-based modules (not JPA). Every write is `… WHERE revision = :expected`; deleting a default-equal override is revision-guarded too. |
| Storage | GCS (the object store the codebase already uses) in prod, local filesystem in dev; served at `{public-base-url}/mail/{sha256}.{ext}`. In dev the API serves them anonymously at `/api/v1/public/mail-assets/**` with `Cache-Control: public, max-age=31536000, immutable`. A nightly job removes never-referenced assets older than 30 days. |
| Images | JPEG/PNG are decoded and re-encoded. The JDK has no WebP writer, so WebP keeps its pixels but loses EXIF/XMP chunks and any bytes after the RIFF. GIF is rewritten block by block: frames, graphic control and `NETSCAPE2.0` loop are kept; comments, other application blocks and bytes after the trailer are dropped. |
| `logo_url` | Nullable: with no logo the header shows the brand name as text (the defaults ship without one). |
| ETag | `W/"<sha256>"` (the `"W/<sha>"` written in §5.3 is not a valid weak tag). The hash covers `defaultsVersion`, `settingsRevision`, the sorted `key:locale:revision` of every override and the language — `max(revision)` + `count` alone can repeat after a save on one template and a reset on another. |
| Audit | Written through `AuditRecorder` in the same transaction as the change (actor, request data, before/after diff), so a failed audit insert rolls the change back. There is no separate `AuditLogProjector` consumer. |
| Cache eviction | `EmailContentProvider` evicts its own instance in `@TransactionalEventListener(AFTER_COMMIT)`. `EmailCacheInvalidator` consumes the outbox events (crash-safe, retried) and broadcasts the eviction on the Redis topic `cronos:mail-cache`; every instance applies it once per `eventId`. Without Redis it evicts locally and other instances converge within the 30-minute TTL. |
| `email.compiled` | Caches the render plan (definition + effective content + settings) keyed by both revisions; Thymeleaf keeps the parsed layout in its own template cache. |
| Saves | Content equal to the default deletes the override (history `RESET`, `EmailTemplateResetEvent`). Content equal to the stored override changes nothing (no revision bump, no event). |
| `SecurityNotifier` | In-app notification `EMAIL_SENDER_CHANGED` (category SECURITY) to every other holder of `NOTIFICATION.EMAIL_TEMPLATE.MANAGE` when `sender.fromAddress` or `sender.replyTo` changes. |
| Permission | `NOTIFICATION.EMAIL_TEMPLATE.MANAGE` (risk HIGH), seeded in `V31__email_template_customization.sql` and granted to `ADMIN`. |
