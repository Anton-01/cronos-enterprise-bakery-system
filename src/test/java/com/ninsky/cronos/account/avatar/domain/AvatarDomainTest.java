package com.ninsky.cronos.account.avatar.domain;

import com.ninsky.cronos.account.shared.domain.DomainValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AvatarDomainTest {

    private static final UUID USER = UUID.fromString("3f9c1c9e-8f6a-4a8e-9a57-1f7a2b1c9d10");

    @Test
    void sniffsAcceptedTypesByMagicBytesOnly() {
        assertThat(DetectedImageType.sniff(bytes(0xFF, 0xD8, 0xFF, 0xE0))).containsInstanceOf(DetectedImageType.Jpeg.class);
        assertThat(DetectedImageType.sniff(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))).containsInstanceOf(DetectedImageType.Png.class);
        assertThat(DetectedImageType.sniff("RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1))).containsInstanceOf(DetectedImageType.Webp.class);
    }

    @Test
    void rejectsLookalikesAndOtherFormats() {
        assertThat(DetectedImageType.sniff("RIFF\0\0\0\0WAVEfmt ".getBytes(StandardCharsets.ISO_8859_1))).isEmpty();
        assertThat(DetectedImageType.sniff("GIF89a".getBytes(StandardCharsets.ISO_8859_1))).isEmpty();
        assertThat(DetectedImageType.sniff("<svg xmlns=".getBytes(StandardCharsets.ISO_8859_1))).isEmpty();
        assertThat(DetectedImageType.sniff(bytes(0x89, 0x50, 0x4E))).isEmpty();
        assertThat(DetectedImageType.sniff(new byte[0])).isEmpty();
        assertThat(DetectedImageType.sniff(null)).isEmpty();
        assertThat(DetectedImageType.ACCEPTED).hasSize(3);
    }

    @Test
    void avatarKeyIsContentAddressedAndScopedToTheUser() {
        AvatarKey key = AvatarKey.forContent(USER, new byte[]{1, 2, 3});

        assertThat(key.value()).matches("avatars/" + USER + "/[0-9a-f]{16}\\.jpg");
        assertThat(key).isEqualTo(AvatarKey.forContent(USER, new byte[]{1, 2, 3}));
        assertThat(key).isNotEqualTo(AvatarKey.forContent(USER, new byte[]{1, 2, 4}));
        assertThat(key.userId()).isEqualTo(USER);
        assertThat(key.belongsTo(USER)).isTrue();
        assertThat(AvatarKey.of(USER, key.fileName())).isEqualTo(key);
    }

    @ParameterizedTest
    @ValueSource(strings = {"../../etc/passwd", "x.jpg", "0123456789abcdef.png", "0123456789ABCDEF.jpg", "0123456789abcdef.jpg/.."})
    void rejectsMalformedKeys(String fileName) {
        assertThatThrownBy(() -> AvatarKey.of(USER, fileName)).isInstanceOf(DomainValidationException.class);
    }

    @Test
    void imageDimensions() {
        ImageDimensions dims = new ImageDimensions(8000, 6000);
        assertThat(dims.pixels()).isEqualTo(48_000_000L);
        assertThat(dims.exceedsPixels(AvatarPolicy.MAX_SOURCE_PIXELS)).isTrue();
        assertThat(dims.shorterEdge()).isEqualTo(6000);
        assertThatThrownBy(() -> new ImageDimensions(0, 10)).isInstanceOf(DomainValidationException.class);
    }

    @Test
    void processedImageNeverPrintsBytes() {
        var image = new ProcessedImage(new byte[]{(byte) 0xFF, (byte) 0xD8}, new ImageDimensions(512, 512), new DetectedImageType.Jpeg());
        assertThat(image.toString()).isEqualTo("ProcessedImage[512x512, 2 bytes]");
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }
        return result;
    }
}
