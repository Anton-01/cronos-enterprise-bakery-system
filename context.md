# Architectural Decision Records

## Infrastructure & DevOps

### Docker build: multi-stage, Java 25, non-root

`Dockerfile` has three stages: `builder` (`eclipse-temurin:25-jdk`, compiles the jar via `mvnw
clean package`), and `runtime` (`eclipse-temurin:25-jre-alpine`, copies only `app.jar`, runs as a
non-root `cronos` user). Distroless was evaluated per the ultra-lightweight requirement but
`gcr.io/distroless/java25-debian12` does not exist yet (distroless currently tops out at
`java21-debian12`), so Alpine JRE is the smallest currently-available base for a `java.version=25`
build — revisit once distroless ships a Java 25 image. `docker compose build` (no `target`
override) builds this full three-stage image, which is what a real deploy should use.

**Do not bake `src/main/resources/credentials/gcp-cronos-key.json` into a production image.** It's
already `.gitignore`d, but a Docker image copies the local working tree regardless of git — running
`docker build .` on a machine with that file present embeds a long-lived service-account key inside
every layer of a shippable image. Production should authenticate to GCS/KMS via Workload Identity
(GKE) or an attached service account (Cloud Run/GCE) instead of a key file. This wasn't changed
here — `GcsStorageAdapter`/`GcpKmsAdapter` still read `gcp.credentials-file` — because switching
credential strategy is a code change with its own testing surface, out of scope for the
infra/Dockerization pass. Flagging it as the one piece worth fixing before this image goes to prod.

### Local dev stack (`docker-compose.yml`)

`docker compose watch` runs `app`, `redis`, `mailpit`, `loki`, `promtail`, `prometheus`, `grafana`.
No local Postgres container — the primary DB is Neon (external, serverless), configured purely
through `DB_URL`/`DB_USERNAME`/`DB_PASSWORD` in `.env` (see `.env.example`); the URL must include
`?sslmode=require`.

**Secrets, and how this relates to `KeysPropertiesEnvironmentPostProcessor`** — that post-processor
(the existing qa/prod external-secrets loader, `infrastructure/config/secrets`) is a deliberate
no-op on the `dev` profile unless `CRONOS_KEYS_FILE` is explicitly set (see its class docs); it does
*not* automatically pick up `.env`. So `docker-compose.yml`'s `app` service reads
`DB_URL`/`DB_USERNAME`/`DB_PASSWORD`/`MAIL_PASSWORD`/`JWT_SECRET` as plain environment variables
from `.env` (`env_file:`) — a parallel path, not a rerouting of the existing mechanism. Developers
who already keep those five values in a `keys.properties` file for qa/prod don't have to duplicate
them into `.env`: `docker-compose.keys-file.yml` is an opt-in overlay
(`-f docker-compose.keys-file.yml`, `CRONOS_KEYS_FILE_HOST=/path/to/keys.properties`) that
bind-mounts that file and sets `CRONOS_KEYS_FILE` inside the container, so the same
`KeysPropertiesEnvironmentPostProcessor` loads it exactly as it would for qa/prod. It's opt-in
rather than the default specifically because `CRONOS_KEYS_FILE` makes loading *mandatory* — wiring
it in unconditionally (e.g. defaulting the mount to `keys.properties.example`) would mean the
committed placeholder's `DB_PASSWORD=changeme` silently wins over a real `.env` value, since the
post-processor adds its property source with the highest priority (`addFirst`). Keeping it opt-in
avoids that footgun for the default path (which is what's actually been run end-to-end, see below).

- **Hot reload** — `app` builds only to the `builder` Dockerfile stage (JDK + resolved deps +
  compiled source) and runs `mvnw spring-boot:run -Dspring-boot.run.profiles=dev` instead of the
  packaged jar. `develop.watch` uses `action: sync+restart` (not plain `sync`) for
  `src/main/java`/`src/main/resources`: `spring-boot:run` only compiles at process start, and
  `spring-boot-devtools`' own in-JVM restart watches `target/classes`, not `src/` — a bare file sync
  with no restart leaves the container running stale compiled classes forever, verified the hard
  way (`docker compose watch` reported the sync but the app never restarted until `sync+restart` was
  wired in). `sync+restart` re-runs `mvnw spring-boot:run` after the file lands, which recompiles
  just the changed sources and comes back up in ~5s — still no Docker image rebuild. Editing
  `pom.xml` triggers a full `rebuild` instead, since dependencies changed. `spring-boot-devtools`
  stays in `pom.xml` (`optional=true`, never ships in the runtime image) for its dev-only side
  effects (disabled template/resource caching, LiveReload), not for its restart mechanism. Command:
  `docker compose watch`.
- **Redis** — backs the existing `TokenBlacklistService` (session/token blacklist,
  `spring.data.redis.*`, now parameterized via `REDIS_HOST`/`REDIS_PORT`/`REDIS_PASSWORD` instead of
  being unconfigured) and a new standalone `RedissonClient` bean (`RedissonConfig`, package
  `infrastructure.config.redis`) for distributed locks on concurrent writes (e.g. recipe/quote
  edits). Deliberately wired by hand off `spring.data.redis.host/port` rather than via
  `redisson-spring-boot-starter`, which auto-replaces Spring Data's `RedisConnectionFactory` and
  would have silently repointed `TokenBlacklistService`'s `StringRedisTemplate` at a second,
  independently-configured connection.
- **Mailpit** — dev profile (`application-dev.yml`) overrides `spring.mail.host/port` to the
  `mailpit` container (no auth, no TLS) instead of SendGrid, so password-reset/new-device/share
  emails are inspectable at `http://localhost:8025` instead of actually sending.
- **Loki + Promtail + Grafana** — Promtail tails the same rolling JSON file
  `logs/cronos-ninsky.log` the app already writes (`logback-spring.xml`'s `FILE_JSON` appender,
  bind-mounted read-only into the `promtail` container), ships it to Loki, and Grafana is
  pre-provisioned (`docker/grafana/provisioning/datasources`) with both Loki and Prometheus as
  datasources — open `http://localhost:3000` and query by `traceId` (kept as an extracted Loki
  field, deliberately *not* a label — it's unique per request, and a label with unbounded
  cardinality blows up Loki's index) to follow one request's log lines end-to-end, e.g. across
  `AuthenticationService` → `CacheMonitoringAspect` → `ErrorInterceptorAspect`.
- **Prometheus** — scrapes `app:9192/actuator/prometheus` (see below). `http://localhost:9090`.

### Actuator: separate internal port, not under `/api/v1`

`management.server.port=9192` runs actuator on its own port, independent of
`server.servlet.context-path=/api/v1` — endpoints are `/actuator/health` and
`/actuator/prometheus`, not `/api/v1/actuator/...`. In `docker-compose.yml` this port is `expose`d
(container-network only) rather than `ports`-published, so it's reachable from `prometheus` but not
from the host/internet — that's the actual security boundary. Base `application.yml` includes only
`health,prometheus`; `application-dev.yml` widens the exposure (`info,beans,env,mappings`) for local
debugging only.

`/actuator/**` is also explicitly `permitAll()`'d in `SecurityConfig`'s `authorizeHttpRequests`.
This looks redundant with network isolation until you actually run it: Spring Boot's separate
management port still gets a **child context that reuses the app's `@Bean SecurityFilterChain`**
(confirmed empirically — before this permitAll was added, curling `:9192/actuator/health` returned
the app's own `AUTHENTICATION_FAILED` JSON error, because `.anyRequest().authenticated()` caught it
too). Prometheus has no JWT to present, so without this exemption the scrape target is permanently
401 and `/actuator/health` is unusable — network isolation alone wasn't sufficient, the app-layer
auth had to be told to back off too.

### Neon HikariCP tuning (`application-prod.properties`, `application-dev.yml`)

Neon's serverless compute auto-suspends on idle and can silently drop/rebuild the underlying
connection — this is almost certainly what produced the `HikariPool-1 - Failed to validate
connection ... This connection has been closed` warnings seen in production logs. Both profiles now
set a short `max-lifetime` (240s) and a `keepalive-time` (120s) so HikariCP proactively recycles
connections before Neon does, instead of handing out one Neon already closed. The base
`application.yml` pool settings (30 min `max-lifetime`, no keepalive) are unchanged and remain
correct for a persistent local Postgres.

## Persistence & Security

### ADR: Bypass JPA entity hydration for the authentication hot path

**Context.** `UserJpaEntity.email` (and `twoFactorSecret`) are transparently encrypted at the JPA
boundary via `@Convert(converter = EncryptedStringConverter.class)`, which delegates to
`FieldEncryptionService` (AES-256-GCM, envelope-encrypted DEK unwrapped from KMS at startup).
Loading a `UserJpaEntity` through JPA — `UserRepositoryPort.findByUsername`/`findByEmail`/`findById`
— always decrypts `email` (`NOT NULL`, so this happens unconditionally on every load), and
`twoFactorSecret` when present.

`AuthenticationService.login()` was doing exactly that as its very first step, before Spring
Security had validated anything. `logout()` did the same just to resolve a user id. This meant:

- Every login attempt — including a wrong password, a nonexistent username, or an already-locked
  account — decrypted the target user's email, for no reason (none of those checks need it).
- Any row whose `email` fails AES-GCM tag verification crashes the request with
  `AEADBadTagException: Tag mismatch`, surfaced as an opaque 500 instead of a 401/423. Two real
  causes observed: (1) legacy plain-text rows written before `EncryptedStringConverter` existed,
  and (2) the local/dev `kms.provider=local` DEK is regenerated on every restart unless
  `kms.local.master-key`/`kms.wrapped-data-key` are persisted, permanently orphaning anything
  encrypted in a prior process.

**Decision.** `AuthenticationService.login()`/`logout()` now resolve the user via the existing
`UserAuthLookupPort` → `JdbcUserAuthAdapter` → `AuthUserProjection` read model (raw `JdbcTemplate`,
minimal columns, no JPA entity graph) — the same lean path `CustomUserDetailsService` already used
for the actual password check. The full JPA `User` aggregate (email/2FA-secret decryption) is only
loaded once a password has been confirmed correct, when the rest of the flow (JWT claims, session
row, `LoginResponse.email`) genuinely needs it.

This is deliberately reusing the pre-existing `UserAuthLookupPort`/`AuthUserProjection` pattern
rather than introducing a second, parallel JDBC read-model — one lean auth projection, not two.

**Why:** to prevent heavy JPA entity graph hydration, avoid unnecessary AES-256-GCM KMS decryption
overhead on sensitive fields, and prevent `AEADBadTagException` during the authentication phase.

**Defense in depth.** `FieldEncryptionService.decrypt()` also no longer hard-crashes on legacy
plain-text values: anything that isn't valid Base64 (a real email always contains `@`/`.`, both
illegal in Base64) is returned as-is, logged as a `WARN`. A value that *is* valid Base64 but fails
GCM tag verification is deliberately **not** treated as plaintext — that's ambiguous with genuine
corruption or a lost/rotated DEK, so it still throws, now with a message pointing at the KMS
persistence issue instead of a bare stack trace. This heuristic does not cover legacy plain-text in
fields whose plaintext form is itself valid Base64 (e.g. a Base32 TOTP secret) — that class of
migration was out of scope here and would need explicit, deliberate handling if it turns up.

**Consequence.** A nonexistent username, an already-locked account, or a wrong password never
touch field decryption anymore. A correct password for a user with a genuinely corrupted (KMS-key
mismatch) email still throws — that's a data problem no query shape fixes; persisting
`kms.local.master-key`/`kms.wrapped-data-key` (see `keys.properties.example`) prevents new
occurrences of it going forward.

**Bugs fixed incidentally while touching this code** (found during the refactor, not introduced by
it): `login()`'s failure path called `AccountLockoutService.handleFailedLogin(user)` but never
persisted the result — failed-attempt counters and lockouts were computed in memory and discarded.
Also, an invalid 2FA code was double-recorded: the explicit `BadCredentialsException` thrown for it
used to re-enter the same outer `catch (BadCredentialsException e)` block that handles a wrong
password, double-incrementing the lockout counter and overwriting the login-history failure reason.
Both are fixed by narrowing the `catch` to wrap only the `authenticationManager.authenticate(...)`
call, so a 2FA failure and a password failure are two independent, single-handled paths.
