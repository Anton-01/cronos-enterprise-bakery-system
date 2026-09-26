# Account Settings module (profile · avatar · fiscal data)

Backend for the Angular "Account Settings" screens. Every endpoint acts on the authenticated user
only (identity from the access token's `userId` claim via `CurrentUserProvider`); there is no `{id}`
in any path.

| Method | Path (`/api/v1` +)       | Use case                    | Notes |
|--------|--------------------------|-----------------------------|-------|
| GET    | `/users/me`              | `GetMyProfileUseCase`       | adds `avatarUrl`; `ETag` = user version |
| PUT    | `/users/me`              | `UpdateMyProfileUseCase`    | full replace, `null` clears; optional `If-Match`; 30 writes/h |
| PUT    | `/users/me/avatar`       | `UploadAvatarUseCase`       | multipart `file`; 413 / 415 / 400 `file`; 10 uploads/h |
| DELETE | `/users/me/avatar`       | `RemoveAvatarUseCase`       | idempotent, `data: null` |
| GET    | `/users/me/fiscal`       | `GetMyFiscalDataUseCase`    | `data: null` when not registered; `Cache-Control: no-store` |
| PUT    | `/users/me/fiscal`       | `UpsertMyFiscalDataUseCase` | upsert; SAT / CFDI 4.0 validation; optional `If-Match`; 30 writes/h |
| POST   | `/auth/change-password`  | `UserService#changePassword`| new endpoint (did not exist); per-field errors |
| GET    | `/public/avatars/{userId}/{hash}.jpg` | —              | serves avatars when `app.avatars.public-base-url` points at the API |

OpenAPI: all of the above are documented (with contract examples) under the **Account Settings** tag
at `/api/v1/swagger-ui.html`.

## Layout

```
account/
  shared/   api (error mapper, ETags, messages, OpenAPI examples) · application/port (CurrentUserProvider,
            AuditTrail, AccountRateLimiter) · domain (sealed AccountDomainError, sealed AuditChange, PiiMasker,
            ExpectedVersion) · infrastructure (security, audit writer, Bucket4j limiter, strict JSON, MapStruct config)
  profile/  api · application · domain (UserAccount, ProfileUpdate, E164Phone) · infrastructure (JPA adapter,
            UserProfileMapper, V7 legacy-phone Java migration, auth-cache eviction)
  avatar/   api · application (ports AvatarStorage, ImageProcessor) · domain (AvatarKey, DetectedImageType,
            ImageDimensions) · infrastructure (ImageIO pipeline, local FS + GCS adapters, after-commit cleanup)
  fiscal/   api (DTOs + @ValidRfc, @NoCorporateSuffix, @MxZipCode, @MexicanState, @SatTaxRegime, @RegimeMatchesRfc)
            · application (FiscalRule beans) · domain (Rfc, TaxpayerIdentity, TaxRegime, MexicanState, MxZipCode,
            LegalName, FiscalAddress, FiscalData) · infrastructure (JPA entity/embeddable, FiscalDataMapper, zip catalog)
```

The domain packages are framework-free (Jackson annotations on the two code enums and libphonenumber
are the only imports), so they compile and test in isolation.

## Configuration

```yaml
spring.servlet.multipart: { max-file-size: 2MB, max-request-size: 3MB }
server.tomcat.max-swallow-size: 10MB       # client gets the 413 envelope instead of a reset
app.avatars:
  storage: local | gcs                     # AVATAR_STORAGE (prod default: gcs)
  public-base-url: ...                     # AVATAR_PUBLIC_BASE_URL — CDN origin in prod
  local-directory: ./uploads               # AVATAR_LOCAL_DIR
  gcs-bucket: (defaults to gcp.bucket-name)
app.account.rate-limit: { avatar-uploads-per-window: 10, profile-writes-per-window: 30, fiscal-writes-per-window: 30, window: 1h }
```

Replace the accept-all SAT zip-code check by declaring any `ZipCodeCatalog` bean.

## Conflicts with the prompt, and how they were resolved

Rule applied: the frontend contract wins for anything on the wire; the existing codebase wins for
internal structure — except the package layout, where the prompt's detailed §3.1 was followed.

| # | Prompt said | Codebase has | Resolution |
|---|---|---|---|
| 1 | Success envelope `{success, message, data, timestamp}` | `EnvelopeResponseBodyAdvice` rewrites every `ApiResponse` into `{meta:{traceId,timestamp}, status:"SUCCESS", message, data}` — this is what the frontend already receives everywhere | Kept the real wire format. `data` is now always serialized (`data: null`), which the old `NON_NULL` envelope dropped. |
| 2 | `errors[].field` is `string\|null` | `ApiError` was `NON_NULL` (key omitted) | `field` is now always serialized (explicit `null`). Additive for all endpoints. |
| 3 | Error codes: closed set | Existing handlers emit `VALIDATION_FAILED` as an `errors[].code` in some paths | Account paths emit only the closed set. Pre-existing handlers were not changed except where noted below. 412 → `SYSTEM_RESOURCE_CONFLICT`; 413/415 → `VALIDATION_FIELD_ERROR` on `file`; 429 → `VALIDATION_ERROR` (no dedicated code in the closed set). |
| 4 | `users` holds firstName/lastName/phone | They live in `user_profiles`; `phone_number` is AES-GCM encrypted (`EncryptedStringConverter`) | Profile writes touch both rows; the `users` row is always bumped so its `@Version` is the profile ETag. Legacy-phone migration is a Java Flyway migration (must decrypt/re-encrypt), not SQL. |
| 5 | JWT resource server, identity = `sub` | Custom `JwtAuthenticationFilter`; `sub` = **username**, principal was reloaded by username | Would have logged users out on rename. Filter now resolves the principal by the immutable `userId` claim (`UserAuthLookupPort#findById`, cached as `id:{uuid}`); renames evict both cache keys after commit. |
| 6 | S3 for prod | Storage is Google Cloud Storage (`GcsStorageAdapter`) | `GcsAvatarStorage` (+ local FS for dev). No AWS SDK added. |
| 7 | New `audit_log` table (id, occurred_at, actor_id, …, changes JSONB) | V4 `audit_log` already exists (immutable, trigger-protected) with the same concepts under other names | Extended it (`trace_id`, `changes JSONB`) and reused `AuditLogPort`; `occurred_at`=`created_at`, `actor_id`=`actor_user_id`, `resource_*`=`target_*`, `ip`=`ip_address`. |
| 8 | `AuditorAware<UUID>` | `AuditorAware<String>` (username) on every entity | Kept (changing it rewrites `created_by` semantics app-wide). |
| 9 | `ValidationMessages_*.properties`, `messages_es_MX` | Bundles are `i18n/messages{,_en,_es}.properties` resolved through `ValidationConfig`; `RequestLocaleResolver` maps any non-English to `es` | Keys added to the existing bundles; `es-MX` resolves to `messages_es`. |
| 10 | `ApiErrorDetail` record | `ApiError(code, message, field, imageUrl)` | Reused `ApiError`. |
| 11 | `UserResponse`, `UpdateProfileRequest` | Both existed (old request had `businessName/businessType`) | `UserResponse` extended with `avatarUrl`; old `UpdateProfileRequest`, `UserController` and `UserService#updateUserProfile` removed (superseded — they also mapped `/users/me`). |
| 12 | "POST /auth/change-password (existing)" | No such endpoint; only `UserService#changePassword` | Added the endpoint. Wrong current password is a 400 on `currentPassword` (not 401, which the SPA would treat as an expired session). |
| 13 | fail-on-unknown-properties | Spring Boot default (lenient) | Scoped: `@RejectUnknownProperties` DTOs only, via a Jackson `DeserializationProblemHandler`. Global behaviour unchanged. |
| 14 | Rate limiting | `RateLimitInterceptor` is per-IP and reads `@RateLimit`, while controllers are annotated `@RateLimitEndpoint` — i.e. currently inert | Per-user Bucket4j limiter behind `AccountRateLimiter` in the application layer. The existing interceptor was left alone. |

## Other deliberate deviations

- **Sealed error hierarchy has three extra variants**: `WriteRateLimited` (profile/fiscal 429; `UploadRateLimited`
  is avatar-specific), `VersionMismatch` (412). Still exhaustive, no `default`.
- **`@AfterMapping` for `taxpayerType`/`avatarUrl`**: records are immutable targets, so these are `@Named`
  qualifier mappings (`taxpayerTypeOf`, `avatarUrl`) instead — same effect, still derived in the mapper.
- **CHAR(n) columns** are `VARCHAR(n)` + exact-length `CHECK`s: CHAR blank-pads (12-char RFCs) and
  `ddl-auto=validate` maps `String` to VARCHAR.
- **Validation messages**: domain keys are `MessageFormat` (`{0}`), Bean Validation keys use `{max}`; they
  never share a key (a `{max}` pattern with arguments throws inside `MessageFormat`).
- **Duplicate errors per field**: the global 400 handler now sorts (field, required-first, constraint) before
  de-duplicating, because Hibernate Validator returns violations in hash order — "first error per field
  wins" is otherwise not stable. Applies to all endpoints.
- **New global mappings** (were 500s): optimistic-lock → 409 `SYSTEM_RESOURCE_CONFLICT`,
  `MaxUploadSizeExceededException` → 413, `MissingServletRequestPartException` → 400,
  `HttpMediaTypeNotSupportedException` → 415, `AccessDeniedException` → 403.
- **Avatar crop**: the server center-crops to a square before scaling (it never trusts the client crop).
- **`If-Match: *`** is treated as "no precondition" even when the fiscal record does not exist yet.
- **CORS** now exposes `ETag`, `Retry-After`, `X-Trace-Id` so the SPA can read them.
- `DELETE /users/me/avatar` is not rate-limited and takes no `If-Match` (idempotent by contract).

## TODO

- **SAT zip catalog**: plug a real `ZipCodeCatalog` (SAT publishes c_CodigoPostal as XLS); the default
  accepts every well-formed code. Could also cross-check zip ↔ state.
- **Avatar CDN**: set `AVATAR_PUBLIC_BASE_URL` in prod to the CDN in front of the bucket; objects are
  written with `Cache-Control: public, max-age=31536000, immutable`.
- **GCS contract test**: add an `AvatarStorageContractTest` subclass against fake-gcs-server.
- **Distributed rate limits**: buckets are per instance; use `bucket4j-redis` (Redisson is already wired)
  behind the same `AccountRateLimiter` port when running more than one replica.
- **Orphan sweeper**: a failed post-commit delete leaves an orphan (logged). A periodic job can list
  `avatars/{userId}/` and delete keys the user no longer references (delete is idempotent).
- **Username case duplicates**: `V6` creates `UNIQUE (lower(username))` and fails if existing data has
  case-only duplicates — resolve before deploying.
- **Password change** does not revoke other sessions (not requested by the contract).
