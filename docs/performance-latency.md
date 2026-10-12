# Latency & Throughput — Backend Performance Brief

> **Audience:** the Claude agent (or engineer) that owns the Cronos backend (Java 25 + Spring Boot, container `cronos-app`).
> **Frontend:** `cronos-ui`, branch `feature/auth-quotes-calendar-notifications`. The UI side of this work is already done (see §7).
> **Read first:** `docs/api/iam-and-finance.md` §0–§1 and `docs/api/auth-quotes-calendar-notifications.md` §1 and §8. Every engineering standard there applies here too: constructor injection only (**never `@Autowired`**, enforced by ArchUnit), English Javadoc, side effects as domain events through the transactional outbox, idempotent consumers, correlation/causation/trace ids on every event.

---

## 0. Agent brief

Sign-in takes **2–4 s** and every paginated screen takes **0.5–3 s** to show data. Both a Spring Boot API and an Angular SPA should do these in well under 300 ms. The frontend has been profiled and trimmed (§7). **Nearly all of the remaining time is spent on the server, and most of it goes to database round trips.** The job is to measure, then remove round trips, then make each one cheaper.

Do the work in the order of §3 (P0 → P2). After each step, record before/after numbers in the table in §6. Do not change any response shape except the additions listed in §5.

---

## 1. Evidence (measured 2026-10-11 on the dev stack)

### 1.1 Topology

| Piece | Where | Note |
|---|---|---|
| API | Docker (`cronos-app-1`) on the developer's Mac, `SPRING_PROFILES_ACTIVE=dev` | Java 25 |
| PostgreSQL | **Neon serverless, `us-east-2` (AWS Ohio), through the `-pooler` endpoint** | TCP connect from the Mac: **~70 ms per round trip** |
| Redis | Local container | ~1 ms per command. Fine |
| Hikari | `maximumPoolSize = 5` | `connections.creation.max` = **1.65 s** (TLS plus Neon compute wake-up) |

**Every SQL statement, `BEGIN`, `COMMIT` and connection validation costs about 70 ms of network time before Postgres does any work.** An endpoint that makes 6–8 round trips cannot be faster than 0.5 s, however good the SQL is.

### 1.2 Mean server time per endpoint (Micrometer `http.server.requests`, last 24 h)

| Endpoint | Mean | What it should cost |
|---|---:|---|
| `POST /auth/login` | **2 070 ms** (max **3 776 ms**) | 1 user query + bcrypt + 1 write transaction |
| `POST /auth/login` (unknown user, 401) | ~1 050 ms (`lookup;dur≈70–135`) | dummy bcrypt only |
| `GET /quotes/stats` | 955 ms | 1 aggregate query |
| `GET /recipes` | 897 ms | 1–2 queries |
| `GET /notifications/unread-count` | 841 ms | 1 indexed aggregate, or a Redis counter |
| `GET /users/me` | 814 ms | 1 query |
| `GET /ingredients/{id}` | 814 ms | 1–2 queries |
| `GET /notifications` | 767 ms | 1–2 queries |
| `POST /notifications/stream/ticket` | 767 ms | **0 DB queries** (a Redis `SET NX EX`) |
| `GET /quotes` | 746 ms | 1–2 queries |
| `GET /ingredients/stats` | 628 ms | 1 aggregate |
| `GET /allergens` | 591 ms | cached reference data |
| `GET /ingredients` | 562 ms | 1–2 queries |
| `GET /measurement-unit/catalog`, `/category`, `/recipe-sections`, `/user-fixed-cost` | 430–460 ms | cached reference data |
| `GET /ingredients` with **no token** (401) | **106 ms** | < 5 ms. Something in the filter chain does I/O before rejecting |
| `GET /auth/availability` | 211 ms | 1 query |
| `GET /auth/password-policy` | 220 ms cold, 5 ms warm | the cache expires too fast |

The **floor is ~430 ms even for trivial reference lists.** That is about 6 round trips at 70 ms, so a fixed per-request overhead (security filter, session check, transaction bracketing, repeated lookups) runs before the endpoint does any real work.

### 1.3 Repository hot spots (`spring.data.repository.invocations`)

- `CategoryJpaRepository.findAll`: called most often, **~158 ms average**. An **unfiltered `findAll`** for a type-scoped screen.
- `TaxRateRepository.findByIsDefaultTrue`, `CurrencyRepository.findByIsDefaultTrue`, `FinanceSettingsRepository.findById`: each called again and again for the same value. Reference data with no cache.
- `UserJpaRepository.findById` / `findByUsername`: 130–200 ms each. Check whether the auth filter loads the user on **every** request.
- `RefreshTokenJpaRepository.revokeTokensBySessionId`: 335 ms.
- During sign-in, these all run **synchronously** on the request: `UserSessionJpaRepository.save`, `RefreshTokenJpaRepository.save`, `DeviceFingerprintJpaRepository.findBy…/save`, `SecurityNotificationJpaRepository.save`. That makes about 8–12 sequential round trips (≈0.6–0.9 s of network alone) before bcrypt runs.

### 1.4 Browser constraints that make server latency worse

- Over HTTP/1.1 the browser opens **at most 6 connections per origin**, and the SSE stream (`GET /notifications/stream`, mean lifetime 543 s) holds one of them permanently. Right after sign-in the UI used to fire 8 requests together. The UI now staggers them (§7), but the server should also serve **HTTP/2** in every deployed environment (§3, P1-6).
- With `maximumPoolSize = 5`, a page that fires 5–6 API calls at once queues on Hikari. If OSIV is on (Spring's default), **each SSE stream may also hold a pooled connection for its whole lifetime** (see P0-3).

---

## 2. Targets (definition of done)

Measure with the API and DB **in the same region** (or a local Postgres in dev):

| Metric | Target |
|---|---|
| `POST /auth/login` p95 (no 2FA) | **< 350 ms**, bcrypt included |
| Paginated list (`GET /ingredients`, `/recipes`, `/quotes`, `/notifications`…) p95 | **< 120 ms** |
| `/stats`, `/unread-count`, `/users/me` p95 | **< 60 ms** |
| Cached reference data (units, categories, allergens, sections, currencies, tax rates) p95 | **< 15 ms** (or `304`) |
| Statements per request | **≤ 3** for every read endpoint, asserted in tests (§4) |
| Unauthenticated `401` | **< 5 ms**, with zero DB or Redis I/O |

---

## 3. Work items

### P0: measure first, then remove the fixed overhead

**P0-1 · Make round trips visible.**
Add `datasource-proxy` (or p6spy) in `dev`/`test` only. For every request, emit `Server-Timing: db;dur=<ms>;desc="<n> stmts", app;dur=<ms>`. The `Server-Timing` infrastructure already exists (`lookup;dur`, `app;dur`); extend it. Turn on `spring.jpa.properties.hibernate.generate_statistics=true` in `dev`. Turn on `management.metrics.distribution.percentiles-histogram.http.server.requests=true` and publish p50/p95/p99 to the Grafana stack already in `docker compose`.

**P0-2 · Dev database next to the app.**
Add a `postgres:17-alpine` service to the compose file and point `dev` at it by default. Flyway already builds the schema. Keep Neon as an opt-in profile (`dev-neon`). If Neon has to stay:
- Run the API **in the same AWS region** as the Neon branch (`us-east-2`) in every deployed environment. Never run app and DB in different regions.
- Turn off scale-to-zero (or raise the suspend timeout) on the branch the app uses. A cold compute adds 0.5–3 s to the first request after idle, which matches the occasional > 5 s sign-in.
- Hikari: `maximum-pool-size: 15`, `minimum-idle: 15`, `keepalive-time: 30000`, `max-lifetime` below Neon's idle cut-off (e.g. `240000`), `connection-timeout: 3000`, and a fail-fast `validation-timeout`.
- The `-pooler` endpoint runs PgBouncer in **transaction** mode. Either connect to the direct endpoint from the app (Hikari is already a pool), or set `prepareThreshold=0` so server-side prepared statements don't break. Add `reWriteBatchedInserts=true` and `tcpKeepAlive=true` to the JDBC URL.

**P0-3 · `spring.jpa.open-in-view=false`.**
With OSIV on, the persistence context, and with it a JDBC connection once any lazy load runs, stays open for the whole HTTP request. That includes long-lived **SSE** requests and the time Jackson spends serialising. Turn it off. Fix every `LazyInitializationException` that follows with an explicit fetch plan (P1-2), never by turning OSIV back on. The SSE handler must not touch JPA after it returns the `SseEmitter`. Events reach it through the outbox → in-memory fan-out.

**P0-4 · Stateless authentication filter.**
- A missing or invalid token returns `401` **before** any DB or Redis access. Today a token-less request takes 106 ms; find the I/O (rate limiter? audit insert? session lookup?) and move it after authentication, or make it async.
- A valid JWT is enough to build the `Authentication`: take roles and permissions from the token claims, and **do not load the user per request**.
- Revocation, screen lock and forced sign-out need a per-session check. Keep it in **Redis**, not Postgres: `session:{sid}` → `ACTIVE|LOCKED|REVOKED` with a TTL equal to the refresh-token lifetime, written by the `SessionLocked`/`SessionRevoked`/`UserSignedOut` event handlers. Add a Caffeine near-cache (TTL 5–10 s) in front of it if needed.
- Set the dev log level to `INFO`. `org.springframework.security` is at `DEBUG` today and logs three lines per request. Use an async appender for the JSON logs.

**P0-5 · Sign-in critical path.** Target: one read, bcrypt, one write transaction.
1. **One query** loads everything the decision needs: user + status flags + password hash + role codes + permission codes + 2FA flag. Use a JPQL `JOIN FETCH` / `@EntityGraph`, or an interface projection with `array_agg` for roles and permissions. Look up by `lower(username) = :id OR lower(email) = :id` against unique functional indexes (§3, P1-4).
2. **bcrypt:** check the cost factor. Use 10–12 (~60–250 ms on the container CPU). Keep the dummy-hash comparison for unknown users (timing equalisation), with the **same** cost. If the stored hash's cost differs from the configured cost, rehash after a successful match, inside the same write transaction.
3. **One write transaction:** insert `user_session` + insert `refresh_token` (batched), reset the failed-attempt counter, and append the `UserSignedIn` outbox row. Nothing else runs on the request thread.
4. **Everything else becomes an outbox consumer** of `UserSignedIn` / `SignInFailed`: login history, device-fingerprint upsert, the `NEW_SIGN_IN` security notification and its email/push, audit, and `last_login_at`. All of them idempotent, keyed by `eventId`.
5. **Failed-attempt counters and the lockout window go in Redis** (`INCR` + `EXPIRE`), with the durable lock state written through an event. A bad password should not cost a Postgres write on the request path.
6. Mark read-only paths `@Transactional(readOnly = true)`. Keep transactions as short as possible, and never hold one across bcrypt.

### P1: make every read endpoint ≤ 3 statements

**P1-1 · Reference data is cached.**
Units catalog, categories (per type), allergens, recipe sections, currencies, tax rates, the default currency/tax rate and finance settings defaults: put them in a **Caffeine** cache (per tenant/user where scoped), invalidated by the domain events those catalogs already publish on change. Add `ETag` (a version or hash of the cached payload) plus `Cache-Control: private, max-age=60`, and answer `If-None-Match` with `304`. `ETag` is already in `Access-Control-Expose-Headers`. Stop calling `findByIsDefaultTrue` / `findById` again within the same request. `CategoryJpaRepository.findAll` must become a type-filtered query.

**P1-2 · No N+1, explicit fetch plans.**
- Lists return **DTO projections** (constructor expressions or interface projections), not entities serialised by Jackson.
- To-many data on a page (ingredient allergens, recipe sections, quote lines) loads in **one** follow-up query `WHERE parent_id IN (:pageIds)`. Never use `JOIN FETCH` on a collection with `Pageable`: Hibernate paginates in memory (HHH90003004).
- Set `hibernate.default_batch_fetch_size=50` as a safety net, not as the plan.

**P1-3 · One statement for page + total.**
`Page<T>` fires a second `count(*)` round trip. For the main grids, use `SELECT …, count(*) OVER () AS total … LIMIT :size OFFSET :offset` (native query or jOOQ/JdbcClient) and build the `Page` from it. Keep the response shape unchanged (`content`, `totalElements`, `number`, `size`…).

**P1-4 · Indexes.** Verify every list and filter with `EXPLAIN (ANALYZE, BUFFERS)` and add at least:
- `users`: unique `lower(username)`, unique `lower(email)`.
- `refresh_token(token_hash)` unique. Store hashes, not raw tokens. Also `refresh_token(session_id)`.
- `user_session(user_id) WHERE is_active`.
- `notification(user_id, created_at DESC)` and a **partial** `notification(user_id, category) WHERE read_at IS NULL` for unread counts.
- `ingredient(owner_id, lower(name))`, `recipe(owner_id, lower(name))`, `quote(owner_id, status, created_at DESC)`, `calendar_event(owner_id, starts_at)`.
- For the text search on names/folios: `pg_trgm` + GIN on `lower(name)` (the UI sends `q` debounced by 300 ms).

**P1-5 · Aggregates in one query, briefly cached.**
- `/ingredients/stats`, `/recipes/stats`, `/quotes/stats`: **one** `SELECT count(*) FILTER (WHERE …) AS …` per endpoint. Cache per owner for 30–60 s and evict on the entity's create/update/delete events.
- `/notifications/unread-count`: one `GROUP BY category` on the partial index. Even better, keep a Redis hash `unread:{userId}` updated by the notification events, with a fallback to SQL on a miss.
- `/users/me`: one query (user + profile + roles) and a short Caffeine cache evicted by `ProfileUpdated`.

**P1-6 · Transport.**
- `server.compression.enabled=true` (`application/json`, `text/event-stream` **excluded**), `min-response-size=1KB`.
- **HTTP/2** at the edge (nginx / ALB / Cloud Run) in every deployed environment, so the six-connection limit and the SSE socket stop serialising requests.
- `spring.threads.virtual.enabled=true` (Java 25). Size Hikari for it (P0-2), or virtual threads just queue on the pool.
- Keep `Access-Control-Max-Age: 3600` (already set). The UI now calls the API same-origin in dev too, so preflights are gone.

**P1-7 · `POST /notifications/stream/ticket` touches Redis only.**
`SET stream-ticket:{uuid} {userId}:{sessionId} NX EX 60`. Consume it with `GETDEL` when the stream opens. No DB.

### P2: fewer requests per screen, and keep it from regressing

**P2-1 · `LoginResponse.user`** (additive, §5.1). Return the signed-in user's summary with the tokens, so the UI does not call `GET /users/me` right after sign-in.

**P2-2 · `GET /dashboard/summary`** (new, §5.2). One endpoint and one SQL statement for the four dashboard counters. The UI calls four endpoints today.

**P2-3 · Load test and budget.**
Add a k6 (or Gatling) script under `perf/` that signs in and walks dashboard → ingredients → recipes → quotes with 20 virtual users. Fail CI when p95 exceeds §2. Run it against Testcontainers Postgres.

**P2-4 · Production sanity checks.**
Run the app with `-XX:+UseZGC -XX:MaxRAMPercentage=75`, and lazy-initialise only beans that are off the request path. Wire `/actuator/health` readiness to the DB, with a 1 s timeout. Raise an alert when Hikari `pending > 0` lasts more than 30 s.

---

## 4. Tests that lock the gains in

- **Statement-count assertions** (datasource-proxy `QueryCountHolder`, Testcontainers Postgres): every list endpoint ≤ 3 statements, `/users/me` = 1, the `/stats` endpoints = 1, `POST /auth/login` ≤ 2 reads + 1 write transaction, `401` without a token = 0.
- **ArchUnit:** no `@Autowired`; no controller method annotated `@Transactional`; no repository call from `..security..` filters except through the session-state port (Redis).
- **Outbox:** `UserSignedIn` is stored atomically with the session. Its consumers (history, fingerprint, notification, audit) are idempotent under redelivery and carry the correlation/causation/trace ids.
- **Cache invalidation:** editing a category, unit or allergen evicts the cache, and the next `GET` returns a new `ETag`.

---

## 5. Contract additions (all additive, nothing breaks)

### 5.1 `POST /auth/login` and `POST /auth/login/two-factor`: optional `user`

```jsonc
{
  "success": true,
  "data": {
    "accessToken": "…", "refreshToken": "…", "username": "ana", "email": "ana@x.mx",
    "screenLockMinutes": 15, "passwordChangeRequired": false, "requiresTwoFactor": false,
    // NEW — optional; same shape as GET /users/me `data`
    "user": { "id": "…", "username": "ana", "email": "ana@x.mx", "firstName": "Ana", "lastName": "Ruiz", "avatarUrl": null, "roles": ["ADMIN"] }
  }
}
```

### 5.2 `GET /dashboard/summary`

```jsonc
{ "success": true, "data": { "recipes": 42, "quotes": 17, "ingredients": 230, "fixedCosts": 6 } }
```

Scope: the caller's own data, the same rules as each module's `stats`. Cache per user for 30 s, evicted by the corresponding entity events.

### 5.3 Conditional GET on reference catalogs

`GET /measurement-unit/catalog`, `/category?type=…`, `/allergens…`, `/recipe-sections`, `/finance/currencies…`, `/finance/tax-rates…` send `ETag`, and answer `If-None-Match` with `304 Not Modified`.

---

## 6. Before / after log (fill in)

| Endpoint | Before (mean / max) | After p50 | After p95 | Statements |
|---|---|---|---|---|
| `POST /auth/login` | 2 070 / 3 776 ms | | | |
| `GET /users/me` | 814 ms | | | |
| `GET /notifications/unread-count` | 841 ms | | | |
| `GET /ingredients` | 562 / 1 079 ms | | | |
| `GET /recipes` | 897 ms | | | |
| `GET /quotes` | 746 ms | | | |
| `GET /quotes/stats` | 955 ms | | | |
| `GET /category` | 433 ms | | | |
| `GET /ingredients` (no token, 401) | 106 ms | | | |

---

## 7. What the UI already changed (for context)

- **Dev calls the API same-origin through `proxy.conf.json`** (`environment.apiUrl = '/api/v1'`), the same as production. This removes the CORS preflights and the extra sockets they used.
- **Fewer requests after sign-in:** the shell asks only for the unread **count**. The bell's list loads on the bell's first open, and the SSE ticket/stream and the push worker start when the browser is idle (≤ 2.5 s later).
- **Dashboard tiles render one by one** as each count arrives, instead of waiting for the slowest of the four (`forkJoin`). P2-2 will cut them to one request.
- **The shell and dashboard code is prefetched on the login screen** while the user types, so a successful sign-in only waits on `POST /auth/login`.
- The cached profile is cleared when a new session is established or ended (it used to leak across accounts signed in on the same tab).
