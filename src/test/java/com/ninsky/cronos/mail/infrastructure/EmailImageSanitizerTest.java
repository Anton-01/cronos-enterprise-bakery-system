package com.ninsky.cronos.mail.infrastructure;

import com.ninsky.cronos.infrastructure.exception.ApiErrorCode;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/** §4.5: magic-byte detection, size and dimension limits, metadata removal, GIF animation kept. */
class EmailImageSanitizerTest {

    /** 1×1 frame: graphic control extension + image descriptor + LZW data. */
    private static final String FRAME = "21f90404320000002c000000000100010000020244" + "0100";
    private static final String NETSCAPE = "21ff0b4e45545343415045322e300301000000";

    @Test
    void svgAndHtmlRenamedToPngAreRejectedByTheirBytes() {
        assertInvalid("<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes(StandardCharsets.UTF_8));
        assertInvalid("<html><body><script>alert(1)</script></body></html>".getBytes(StandardCharsets.UTF_8));
        // PNG signature followed by garbage: the type is right, the file is not an image.
        byte[] fake = new byte[64];
        System.arraycopy(HexFormat.of().parseHex("89504e470d0a1a0a"), 0, fake, 0, 8);
        assertInvalid(fake);
    }

    @Test
    void jpegExifIsStrippedByReencoding() throws IOException {
        byte[] jpeg = encode(new BufferedImage(20, 10, BufferedImage.TYPE_INT_RGB), "jpeg");
        byte[] exif = "Exif\0\0GPS-SECRET-48.8584N".getBytes(StandardCharsets.ISO_8859_1);
        ByteArrayOutputStream withExif = new ByteArrayOutputStream();
        withExif.write(jpeg, 0, 2);
        withExif.write(0xFF);
        withExif.write(0xE1);
        withExif.write((exif.length + 2) >> 8);
        withExif.write((exif.length + 2) & 0xFF);
        withExif.write(exif);
        withExif.write(jpeg, 2, jpeg.length - 2);

        EmailImageSanitizer.Result result = EmailImageSanitizer.process(withExif.toByteArray());

        assertThat(result.type()).isEqualTo(EmailImageSanitizer.Type.JPEG);
        assertThat(result.width()).isEqualTo(20);
        assertThat(result.height()).isEqualTo(10);
        assertThat(new String(result.bytes(), StandardCharsets.ISO_8859_1)).doesNotContain("GPS-SECRET").doesNotContain("Exif");
    }

    @Test
    void pngTextChunksAndTrailingPayloadsDoNotSurvive() throws IOException {
        byte[] png = encode(new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB), "png");
        byte[] polyglot = concat(png, "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8));

        EmailImageSanitizer.Result result = EmailImageSanitizer.process(polyglot);

        assertThat(result.type()).isEqualTo(EmailImageSanitizer.Type.PNG);
        assertThat(new String(result.bytes(), StandardCharsets.ISO_8859_1)).doesNotContain("<script>");
    }

    @Test
    void gifKeepsItsFramesAndLoopButLosesCommentsAndTrailingData() throws IOException {
        byte[] gif = HexFormat.of().parseHex("474946383961" + "010001008000" + "00" + "000000ffffff" + NETSCAPE
                + "21fe0c" + HexFormat.of().formatHex("SECRET-NOTE!".getBytes(StandardCharsets.US_ASCII)) + "00"
                + FRAME + FRAME + "3b" + HexFormat.of().formatHex("<html>payload</html>".getBytes(StandardCharsets.US_ASCII)));

        EmailImageSanitizer.Result result = EmailImageSanitizer.process(gif);

        String clean = new String(result.bytes(), StandardCharsets.ISO_8859_1);
        assertThat(result.type()).isEqualTo(EmailImageSanitizer.Type.GIF);
        assertThat(clean).doesNotContain("SECRET-NOTE").doesNotContain("payload").contains("NETSCAPE2.0");
        assertThat(result.bytes()[result.bytes().length - 1]).isEqualTo((byte) 0x3B);
        assertThat(frames(result.bytes())).isEqualTo(2);
    }

    @Test
    void sizeAndDimensionLimits() throws IOException {
        byte[] tooLarge = new byte[EmailImageSanitizer.MAX_BYTES + 1];
        System.arraycopy(HexFormat.of().parseHex("89504e470d0a1a0a"), 0, tooLarge, 0, 8);
        assertThat(catchThrowableOfType(ApiException.class, () -> EmailImageSanitizer.process(tooLarge)).primaryCode())
                .isEqualTo(ApiErrorCode.ASSET_TOO_LARGE);

        byte[] wide = encode(new BufferedImage(4001, 1, BufferedImage.TYPE_BYTE_BINARY), "png");
        ApiException error = catchThrowableOfType(ApiException.class, () -> EmailImageSanitizer.process(wide));
        assertThat(error.primaryCode()).isEqualTo(ApiErrorCode.ASSET_DIMENSIONS);
        assertThat(error.violations().getFirst().field()).isEqualTo("file");
    }

    @Test
    void reprocessingTheSameUploadIsDeterministic() throws IOException {
        byte[] png = encode(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "png");
        assertThat(EmailImageSanitizer.process(png).bytes()).isEqualTo(EmailImageSanitizer.process(png).bytes());
    }

    private static void assertInvalid(byte[] upload) {
        ApiException error = catchThrowableOfType(ApiException.class, () -> EmailImageSanitizer.process(upload));
        assertThat(error.primaryCode()).isEqualTo(ApiErrorCode.ASSET_INVALID_TYPE);
    }

    private static int frames(byte[] gif) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(gif))) {
            ImageReader reader = ImageIO.getImageReadersByFormatName("gif").next();
            reader.setInput(input);
            return reader.getNumImages(true);
        }
    }

    private static byte[] encode(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
