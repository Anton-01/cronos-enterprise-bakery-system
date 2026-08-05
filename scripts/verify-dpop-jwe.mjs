#!/usr/bin/env node
// Phase 4b end-to-end verification harness. Exercises DPoP + JWE against a live running Cronos
// instance, including the negative/tamper cases, plus confirms the legacy plain-Bearer/plain-JSON
// flow is completely untouched. Doubles as a working reference implementation — see
// docs/phase-4b-dpop-jwe-client-integration.md for the same patterns as standalone snippets.
//
// Usage: cd scripts && npm install && BASE_URL=http://localhost:9191/api/v1 npm run verify

import { generateKeyPair, exportJWK, importJWK, SignJWT, CompactEncrypt, compactDecrypt } from 'jose';

const BASE_URL = process.env.BASE_URL ?? 'http://localhost:9191/api/v1';
const USERNAME = process.env.CRONOS_USERNAME ?? 'admin_cronos';
const PASSWORD = process.env.CRONOS_PASSWORD ?? 'SuperAdmin2026!';

let passed = 0;
let failed = 0;

function assert(condition, message) {
    if (!condition) {
        failed++;
        console.error(`  FAIL: ${message}`);
        return false;
    }
    passed++;
    console.log(`  ok: ${message}`);
    return true;
}

function decodeJwtPayload(token) {
    const payload = token.split('.')[1];
    const padded = payload.replace(/-/g, '+').replace(/_/g, '/').padEnd(payload.length + (4 - payload.length % 4) % 4, '=');
    return JSON.parse(Buffer.from(padded, 'base64').toString('utf8'));
}

async function createDpopProof(privateKey, publicJwk, method, url, overrides = {}) {
    return await new SignJWT({ htm: method, htu: url, ...overrides })
        .setProtectedHeader({ alg: 'ES256', typ: 'dpop+jwt', jwk: publicJwk })
        .setIssuedAt(overrides.iat ?? undefined)
        .setJti(overrides.jti ?? crypto.randomUUID())
        .sign(privateKey);
}

async function main() {
    console.log(`Phase 4b verification against ${BASE_URL}\n`);

    // ---- Step 1: DPoP-bound login ----
    console.log('[1] DPoP-bound login');
    const { privateKey, publicKey } = await generateKeyPair('ES256', { extractable: true });
    const publicJwk = await exportJWK(publicKey);
    const loginUrl = `${BASE_URL}/auth/login`;

    const loginProof = await createDpopProof(privateKey, publicJwk, 'POST', loginUrl);
    const loginRes = await fetch(loginUrl, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'DPoP': loginProof },
        body: JSON.stringify({ username: USERNAME, password: PASSWORD }),
    });
    const loginBody = await loginRes.json();
    assert(loginRes.status === 200 && loginBody.status === 'SUCCESS', 'DPoP-bound login succeeds');
    const accessToken = loginBody.data.accessToken;
    const refreshToken = loginBody.data.refreshToken;
    const claims = decodeJwtPayload(accessToken);
    assert(!!claims.cnf?.jkt, 'issued access token carries a cnf.jkt claim (bound)');

    // ---- Step 2: authenticated request via DPoP scheme ----
    console.log('\n[2] Authenticated request via DPoP scheme');
    const meUrl = `${BASE_URL}/users/me`;
    const meProof = await createDpopProof(privateKey, publicJwk, 'GET', meUrl);
    const meRes = await fetch(meUrl, { headers: { 'Authorization': `DPoP ${accessToken}`, 'DPoP': meProof } });
    assert(meRes.status === 200, 'GET /users/me with DPoP scheme + matching proof succeeds');

    // ---- Step 3: replay the same proof (negative) ----
    console.log('\n[3] Replay negative test');
    const replayRes = await fetch(meUrl, { headers: { 'Authorization': `DPoP ${accessToken}`, 'DPoP': meProof } });
    assert(replayRes.status === 401, 'reusing the same DPoP proof is rejected (401)');

    // ---- Step 4: stale iat (negative) ----
    console.log('\n[4] Stale proof negative test');
    const staleProof = await createDpopProof(privateKey, publicJwk, 'GET', meUrl, { iat: Math.floor(Date.now() / 1000) - 300 });
    const staleRes = await fetch(meUrl, { headers: { 'Authorization': `DPoP ${accessToken}`, 'DPoP': staleProof } });
    assert(staleRes.status === 401, 'a proof older than the freshness window is rejected (401)');

    // ---- Step 5: bound token replayed via plain Bearer (negative — downgrade protection) ----
    console.log('\n[5] Downgrade-protection negative test');
    const downgradeRes = await fetch(meUrl, { headers: { 'Authorization': `Bearer ${accessToken}` } });
    assert(downgradeRes.status === 401, 'a bound token presented via plain Bearer is rejected (401)');

    // ---- Step 6: refresh with the same key ----
    console.log('\n[6] Refresh with the same DPoP key');
    const refreshUrl = `${BASE_URL}/auth/refresh`;
    const refreshProof = await createDpopProof(privateKey, publicJwk, 'POST', refreshUrl);
    const refreshRes = await fetch(refreshUrl, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'DPoP': refreshProof },
        body: JSON.stringify({ refreshToken }),
    });
    const refreshBody = await refreshRes.json();
    assert(refreshRes.status === 200 && refreshBody.status === 'SUCCESS', 'refresh with the original key succeeds');
    const newAccessToken = refreshBody.data.accessToken;
    const newRefreshToken = refreshBody.data.refreshToken;
    assert(!!decodeJwtPayload(newAccessToken).cnf?.jkt, 're-issued access token is still bound');

    // ---- Step 7: refresh with a different key (negative) ----
    console.log('\n[7] Refresh with a mismatched key negative test');
    const { privateKey: otherPrivateKey, publicKey: otherPublicKey } = await generateKeyPair('ES256', { extractable: true });
    const otherPublicJwk = await exportJWK(otherPublicKey);
    const wrongKeyProof = await createDpopProof(otherPrivateKey, otherPublicJwk, 'POST', refreshUrl);
    const wrongKeyRes = await fetch(refreshUrl, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'DPoP': wrongKeyProof },
        body: JSON.stringify({ refreshToken: newRefreshToken }),
    });
    assert(wrongKeyRes.status !== 200, 'refresh with a different key than the session was bound to is rejected');

    // ---- Step 8: JWE-encrypted request ----
    console.log('\n[8] JWE-encrypted request');
    const jwkRes = await fetch(`${BASE_URL}/security/jwe-public-key`);
    assert(jwkRes.status === 200, 'GET /security/jwe-public-key succeeds (no auth required)');
    const serverPublicKey = await importJWK(await jwkRes.json(), 'ECDH-ES');

    const activeProofForMe = await createDpopProof(privateKey, publicJwk, 'PUT', meUrl);
    const phoneNumber = `555-${Math.floor(Math.random() * 9000000 + 1000000)}`;
    const encryptedBody = await new CompactEncrypt(new TextEncoder().encode(JSON.stringify({ phoneNumber })))
        .setProtectedHeader({ alg: 'ECDH-ES', enc: 'A256GCM' })
        .encrypt(serverPublicKey);

    const encryptedReqRes = await fetch(meUrl, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/jwe', 'Authorization': `DPoP ${newAccessToken}`, 'DPoP': activeProofForMe },
        body: encryptedBody,
    });
    const encryptedReqBody = await encryptedReqRes.json();
    assert(encryptedReqRes.status === 200 && encryptedReqBody.data.phoneNumber === phoneNumber, 'JWE-encrypted request body is decrypted and applied correctly');

    // ---- Step 9: JWE-encrypted response ----
    console.log('\n[9] JWE-encrypted response');
    const { privateKey: respPrivate, publicKey: respPublic } = await generateKeyPair('ECDH-ES', { crv: 'P-256', extractable: true });
    const responseKeyHeader = Buffer.from(JSON.stringify(await exportJWK(respPublic))).toString('base64url');
    const responseProof = await createDpopProof(privateKey, publicJwk, 'GET', meUrl);

    const encryptedRespRes = await fetch(meUrl, {
        headers: { 'Authorization': `DPoP ${newAccessToken}`, 'DPoP': responseProof, 'X-JWE-Response-Key': responseKeyHeader },
    });
    assert(encryptedRespRes.headers.get('content-type')?.includes('application/jwe'), 'response Content-Type is application/jwe');
    const { plaintext } = await compactDecrypt(await encryptedRespRes.text(), respPrivate);
    const decryptedJson = JSON.parse(new TextDecoder().decode(plaintext));
    assert(decryptedJson.data?.phoneNumber === phoneNumber, 'decrypted JWE response contains the expected data');

    // ---- Step 10: legacy plain Bearer + plain JSON, completely unaffected ----
    console.log('\n[10] Legacy client (plain Bearer + plain JSON) unaffected');
    const legacyLoginRes = await fetch(loginUrl, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: USERNAME, password: PASSWORD }),
    });
    const legacyLoginBody = await legacyLoginRes.json();
    assert(legacyLoginRes.status === 200 && legacyLoginBody.status === 'SUCCESS', 'plain login (no DPoP header) still works');
    assert(!decodeJwtPayload(legacyLoginBody.data.accessToken).cnf, 'unbound login issues a token with no cnf claim');
    const legacyMeRes = await fetch(meUrl, { headers: { 'Authorization': `Bearer ${legacyLoginBody.data.accessToken}` } });
    assert(legacyMeRes.status === 200, 'plain Bearer + plain JSON authenticated request still works');

    console.log(`\n${passed} passed, ${failed} failed`);
    process.exit(failed === 0 ? 0 : 1);
}

main().catch((e) => {
    console.error('Verification script crashed:', e);
    process.exit(1);
});
