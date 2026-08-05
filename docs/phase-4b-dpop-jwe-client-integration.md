# Phase 4b client integration: DPoP + JWE

Both mechanisms are **opt-in**. A client that does nothing keeps working exactly as before —
`Authorization: Bearer <token>`, plain JSON bodies. This guide is for a client that wants the
stronger guarantees: DPoP binds the access token to a key only your client holds (a stolen token
is useless without the private key); JWE encrypts request/response bodies at the application layer,
on top of TLS.

All examples use [`jose`](https://github.com/panva/jose) (`npm install jose`), a small, audited,
zero-dependency JOSE library that works in browsers and Node. A complete, runnable version of this
flow lives at `scripts/verify-dpop-jwe.mjs` in this repo — copy from there if you want working code
rather than assembling snippets.

## DPoP (RFC 9449)

### 1. Generate a key pair once, keep it for the session

```typescript
import { generateKeyPair, exportJWK } from 'jose';

const { privateKey, publicKey } = await generateKeyPair('ES256', { extractable: true });
const publicJwk = await exportJWK(publicKey);
```

Keep `privateKey` in memory for the lifetime of the session (e.g. a module-level variable, or
`IndexedDB` via the non-extractable `CryptoKey` form if you want it to survive a page reload —
outside this guide's scope). Don't persist the raw key material anywhere you don't control.

### 2. Sign a proof for each request

```typescript
import { SignJWT } from 'jose';

async function createDpopProof(method: string, url: string): Promise<string> {
  return await new SignJWT({ htm: method, htu: url })
    .setProtectedHeader({ alg: 'ES256', typ: 'dpop+jwt', jwk: publicJwk })
    .setIssuedAt()
    .setJti(crypto.randomUUID())
    .sign(privateKey);
}
```

`htu` must be the **exact** absolute URL you're calling — scheme, host, path, no query string, no
trailing-slash mismatches. `htm` is the HTTP method, case-insensitive. Proofs are single-use and
expire after 120 seconds server-side (`dpop.proof-max-age-seconds`) — sign a fresh one per request,
never reuse.

### 3. Opt in at login

Send the proof on the login request itself to get a **bound** token back:

```typescript
const loginUrl = 'https://your-api/api/v1/auth/login';

const response = await fetch(loginUrl, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json', 'DPoP': await createDpopProof('POST', loginUrl) },
  body: JSON.stringify({ username, password }),
});
const { data } = await response.json();
const accessToken = data.accessToken; // now bound to your key — decode it yourself to confirm a cnf.jkt claim if you want
```

No `DPoP` header on login → an ordinary unbound token, identical to today. An invalid proof fails
the login outright (the server won't silently downgrade — if you sent a proof, you meant it).

### 4. Use the `DPoP` scheme thereafter, every request

```typescript
const apiUrl = 'https://your-api/api/v1/users/me';

const response = await fetch(apiUrl, {
  headers: {
    'Authorization': `DPoP ${accessToken}`,
    'DPoP': await createDpopProof('GET', apiUrl),
  },
});
```

A bound token presented via plain `Authorization: Bearer` is **rejected** — that's the whole point
(a stolen bound token replayed unbound would otherwise defeat DPoP entirely). Once you've opted in
at login, keep using the `DPoP` scheme for that session.

### 5. Refreshing

Same rule, same key — the refresh request needs a fresh proof from the *same* key pair the session
was originally bound to:

```typescript
const refreshUrl = 'https://your-api/api/v1/auth/refresh';
await fetch(refreshUrl, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json', 'DPoP': await createDpopProof('POST', refreshUrl) },
  body: JSON.stringify({ refreshToken }),
});
```

A different key on refresh is rejected (prevents a stolen refresh token minting a token bound to an
attacker's own key).

## JWE (request/response payload encryption)

### 1. Fetch the server's public key (no auth required)

```typescript
import { importJWK } from 'jose';

const jwkResponse = await fetch('https://your-api/api/v1/security/jwe-public-key');
const serverPublicKey = await importJWK(await jwkResponse.json(), 'ECDH-ES');
```

Cache this — it doesn't change per-request (only on server key rotation, out of scope for now).

### 2. Encrypt a request body

```typescript
import { CompactEncrypt } from 'jose';

const jwe = await new CompactEncrypt(new TextEncoder().encode(JSON.stringify(payload)))
  .setProtectedHeader({ alg: 'ECDH-ES', enc: 'A256GCM' })
  .encrypt(serverPublicKey);

await fetch(apiUrl, {
  method: 'POST',
  headers: { 'Content-Type': 'application/jwe', /* ...auth headers... */ },
  body: jwe, // the compact JWE string IS the body — not JSON-wrapped
});
```

Anything sent with a different `Content-Type` is untouched by the server — this is purely opt-in
per request.

### 3. Request an encrypted response

Generate a fresh ephemeral key pair, send the public half in a header:

```typescript
import { generateKeyPair, exportJWK, compactDecrypt } from 'jose';

const { privateKey: respPrivate, publicKey: respPublic } = await generateKeyPair('ECDH-ES', { crv: 'P-256', extractable: true });
const responseKeyHeader = Buffer.from(JSON.stringify(await exportJWK(respPublic))).toString('base64url');

const response = await fetch(apiUrl, {
  headers: { 'X-JWE-Response-Key': responseKeyHeader, /* ...auth headers... */ },
});

const { plaintext } = await compactDecrypt(await response.text(), respPrivate);
const json = JSON.parse(new TextDecoder().decode(plaintext));
```

Generate a new ephemeral key pair per request (or at least per session) — don't reuse one
long-term, that defeats the point of it being ephemeral. No header → a plain JSON response,
identical to today.

## Notes

- DPoP and JWE are independent — use either, both, or neither, per request.
- A response encrypted with `X-JWE-Response-Key` comes back as `Content-Type: application/jwe`
  with the compact JWE serialization as the raw body (not JSON — `response.text()`, not
  `response.json()`).
- Server-side error responses (401s, validation errors, etc.) get encrypted too if you asked for an
  encrypted response — decrypt first, then check the status code / parse the JSON body underneath.
