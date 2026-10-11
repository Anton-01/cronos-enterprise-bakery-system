package com.ninsky.cronos.notification.infrastructure.push;

import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RFC 8291 / RFC 8188 encryption checked by decrypting as the browser does (written here from the RFC, independently
 * of the sender), and the VAPID JWT checked with the public key.
 */
class WebPushCryptoTest {

    @Test
    void browserCanDecryptTheMessage() throws Exception {
        KeyPair browser = WebPushCrypto.generateKeyPair();
        byte[] uaPublic = WebPushCrypto.encode((ECPublicKey) browser.getPublic());
        byte[] auth = new byte[16];
        new SecureRandom().nextBytes(auth);
        byte[] plaintext = "{\"title\":\"¡Hola!\",\"body\":\"Cotización aceptada\"}".getBytes(StandardCharsets.UTF_8);

        byte[] message = WebPushCrypto.encrypt(plaintext, uaPublic, auth);

        assertThat(decryptAsBrowser(message, (ECPrivateKey) browser.getPrivate(), uaPublic, auth)).isEqualTo(plaintext);
        assertThat(ByteBuffer.wrap(message, 16, 4).getInt()).isEqualTo(4096);
        assertThat(message[20]).isEqualTo((byte) 65);
    }

    @Test
    void refusesPayloadsThatDoNotFitOneRecord() throws Exception {
        KeyPair browser = WebPushCrypto.generateKeyPair();
        byte[] uaPublic = WebPushCrypto.encode((ECPublicKey) browser.getPublic());
        assertThatThrownBy(() -> WebPushCrypto.encrypt(new byte[4000], uaPublic, new byte[16])).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void keysRoundTripThroughTheirEncodings() throws Exception {
        KeyPair pair = WebPushCrypto.generateKeyPair();
        byte[] pub = WebPushCrypto.encode((ECPublicKey) pair.getPublic());
        byte[] priv = WebPushCrypto.encode((ECPrivateKey) pair.getPrivate());
        assertThat(pub).hasSize(65);
        assertThat(priv).hasSize(32);
        assertThat(WebPushCrypto.encode(WebPushCrypto.publicKey(pub))).isEqualTo(pub);
        assertThat(WebPushCrypto.encode(WebPushCrypto.privateKey(priv))).isEqualTo(priv);
    }

    @Test
    void vapidJwtIsSignedWithTheServerKey() throws Exception {
        KeyPair server = WebPushCrypto.generateKeyPair();
        byte[] pub = WebPushCrypto.encode((ECPublicKey) server.getPublic());
        String header = WebPushCrypto.vapidAuthorization("https://fcm.googleapis.com", "mailto:soporte@cronos.mx",
                Instant.parse("2026-10-12T00:00:00Z"), (ECPrivateKey) server.getPrivate(), pub);

        assertThat(header).startsWith("vapid t=").endsWith(", k=" + WebPushCrypto.base64Url(pub));
        String jwt = header.substring("vapid t=".length(), header.indexOf(", k="));
        String[] parts = jwt.split("\\.");
        String claims = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        assertThat(claims).contains("\"aud\":\"https://fcm.googleapis.com\"", "\"exp\":1791763200", "\"sub\":\"mailto:soporte@cronos.mx\"");

        Signature verifier = Signature.getInstance("SHA256withECDSAinP1363Format");
        verifier.initVerify(server.getPublic());
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(verifier.verify(Base64.getUrlDecoder().decode(parts[2]))).isTrue();
    }

    /** RFC 8291 §3.4 and RFC 8188 §2, receiver side. */
    private static byte[] decryptAsBrowser(byte[] message, ECPrivateKey uaPrivate, byte[] uaPublic, byte[] auth) throws Exception {
        byte[] salt = Arrays.copyOfRange(message, 0, 16);
        int idLength = message[20] & 0xff;
        byte[] asPublic = Arrays.copyOfRange(message, 21, 21 + idLength);
        byte[] ciphertext = Arrays.copyOfRange(message, 21 + idLength, message.length);

        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(uaPrivate);
        agreement.doPhase(WebPushCrypto.publicKey(asPublic), true);
        byte[] ecdh = agreement.generateSecret();

        byte[] prkKey = hmac(auth, ecdh);
        ByteBuffer info = ByteBuffer.allocate(14 + 65 + 65 + 1);
        info.put("WebPush: info".getBytes(StandardCharsets.US_ASCII)).put((byte) 0).put(uaPublic).put(asPublic).put((byte) 1);
        byte[] ikm = hmac(prkKey, info.array());
        byte[] prk = hmac(salt, ikm);
        byte[] cek = Arrays.copyOf(hmac(prk, withOne("Content-Encoding: aes128gcm")), 16);
        byte[] nonce = Arrays.copyOf(hmac(prk, withOne("Content-Encoding: nonce")), 12);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] padded = cipher.doFinal(ciphertext);
        int end = padded.length - 1;
        while (padded[end] == 0) {
            end--;
        }
        assertThat(padded[end]).isEqualTo((byte) 2);
        return Arrays.copyOf(padded, end);
    }

    private static byte[] withOne(String label) {
        byte[] bytes = label.getBytes(StandardCharsets.US_ASCII);
        byte[] out = Arrays.copyOf(bytes, bytes.length + 2);
        out[bytes.length] = 0;
        out[bytes.length + 1] = 1;
        return out;
    }

    private static byte[] hmac(byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }
}
