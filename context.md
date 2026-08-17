# Architectural Decision Records

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
