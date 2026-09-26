package com.ninsky.cronos.account.avatar.infrastructure;

import com.ninsky.cronos.account.avatar.domain.ProcessedImage;
import com.ninsky.cronos.account.shared.domain.AccountDomainError.ImageRejected;
import com.ninsky.cronos.account.shared.domain.AccountDomainException;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.CRC32;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class ImageIoImageProcessorTest {

    private final ImageIoImageProcessor processor = new ImageIoImageProcessor();

    @Test
    void reencodesToASquareJpegOfAtMost512() throws IOException {
        ProcessedImage landscape = processor.process(encode(image(600, 400), "png"));
        ProcessedImage large = processor.process(encode(image(1200, 1000), "jpg"));

        assertThat(landscape.dimensions().width()).isEqualTo(400);
        assertThat(landscape.dimensions().height()).isEqualTo(400);
        assertThat(large.dimensions().width()).isEqualTo(512);
        assertThat(large.dimensions().height()).isEqualTo(512);
        assertThat(large.jpeg()).startsWith((byte) 0xFF, (byte) 0xD8, (byte) 0xFF);
    }

    @Test
    void outputCarriesNoExifAndNoAppendedPayload() throws IOException {
        byte[] polyglot = concat(encode(image(300, 300), "jpg"), "<?php system($_GET['c']); ?>PAYLOAD".getBytes(StandardCharsets.ISO_8859_1));

        byte[] jpeg = processor.process(polyglot).jpeg();

        String raw = new String(jpeg, StandardCharsets.ISO_8859_1);
        assertThat(raw).doesNotContain("PAYLOAD").doesNotContain("Exif");
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(jpeg))) {
            assertThat(ImageIO.getImageReaders(in).hasNext()).isTrue();
        }
    }

    @Test
    void rejectsImagesWhoseShorterEdgeIsBelow128() throws IOException {
        assertRejected(encode(image(127, 600), "png"), ImageRejected.Reason.INVALID, "account.avatar.file.tooSmall");
    }

    @Test
    void rejectsNonImagesAsUnsupportedMediaType() {
        assertRejected("%PDF-1.7 not an image".getBytes(StandardCharsets.ISO_8859_1), ImageRejected.Reason.UNSUPPORTED_TYPE,
                "account.avatar.file.unsupportedType");
    }

    @Test
    void rejectsTruncatedFiles() throws IOException {
        byte[] jpeg = encode(image(300, 300), "jpg");
        assertRejected(Arrays.copyOf(jpeg, jpeg.length / 2), ImageRejected.Reason.INVALID, "account.avatar.file.corrupt");
    }

    @Test
    void rejectsDecompressionBombsFromTheHeaderBeforeDecoding() throws IOException {
        // A valid PNG signature + IHDR claiming 10000x10000 (100 MP) and no pixel data at all.
        assertRejected(pngHeaderOnly(10_000, 10_000), ImageRejected.Reason.INVALID, "account.avatar.file.tooManyPixels");
    }

    private void assertRejected(byte[] upload, ImageRejected.Reason reason, String messageKey) {
        AccountDomainException thrown = catchThrowableOfType(AccountDomainException.class, () -> processor.process(upload));
        assertThat(thrown).isNotNull();
        assertThat(thrown.primary()).isInstanceOfSatisfying(ImageRejected.class, rejected -> {
            assertThat(rejected.reason()).isEqualTo(reason);
            assertThat(rejected.field()).isEqualTo("file");
            assertThat(rejected.messageKey()).isEqualTo(messageKey);
        });
    }

    static BufferedImage image(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(Color.ORANGE);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        return image;
    }

    static byte[] encode(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    private static byte[] pngHeaderOnly(int width, int height) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
        byte[] ihdr = ByteBuffer.allocate(13).putInt(width).putInt(height).put((byte) 8).put((byte) 2).put((byte) 0).put((byte) 0).put((byte) 0).array();
        CRC32 crc = new CRC32();
        crc.update("IHDR".getBytes(StandardCharsets.US_ASCII));
        crc.update(ihdr);
        out.write(ByteBuffer.allocate(4).putInt(13).array());
        out.write("IHDR".getBytes(StandardCharsets.US_ASCII));
        out.write(ihdr);
        out.write(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
        return out.toByteArray();
    }
}
