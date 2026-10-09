package com.ninsky.cronos.kitchen.recipe.file;

import com.ninsky.cronos.infrastructure.exception.ApiErrorCode;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.event.IIOReadWarningListener;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Recipe images (§5.7): metadata stripped, a 400 px thumbnail and an 800 px "card" variant (list cards and the book
 * view, baking-studio §2.2). JPEG/PNG are decoded and re-encoded
 * (EXIF/GPS never survive); WebP has no JDK writer, so its EXIF/XMP RIFF chunks are removed instead.
 * Variants are WebP via the system libwebp ({@link WebpEncoder}), JPEG when it is missing. Rejects decompression bombs before decoding.
 */
@Slf4j
public final class ImageSanitizer {

    public static final int THUMBNAIL_EDGE = 400;
    public static final int CARD_EDGE = 800;
    static final long MAX_PIXELS = 40_000_000L;
    private static final float JPEG_QUALITY = 0.9f;
    private static final float WEBP_QUALITY = 82f;
    private static final Set<String> WEBP_METADATA = Set.of("EXIF", "XMP ");

    /** Clean original, thumbnail and card variants, and the decoded size in pixels. */
    public record Result(byte[] image, Thumbnail thumbnail, Thumbnail card, int width, int height) {
    }

    /** An encoded variant with its storage extension and content type. */
    public record Thumbnail(byte[] bytes, String extension, String mimeType) {
    }

    static {
        ImageIO.setUseCache(false);
        ImageIO.scanForPlugins();
    }

    private ImageSanitizer() {
    }

    public static Result process(byte[] upload, SniffedFile type) {
        BufferedImage decoded = decode(upload, type);
        try {
            byte[] clean = switch (type.extension()) {
                case "jpg" -> encode(flatten(decoded, decoded.getWidth(), decoded.getHeight()), "jpeg");
                case "png" -> encode(decoded, "png");
                default -> stripWebpMetadata(upload);
            };
            return new Result(clean, encodeThumbnail(scaled(decoded, THUMBNAIL_EDGE)), encodeThumbnail(scaled(decoded, CARD_EDGE)),
                    decoded.getWidth(), decoded.getHeight());
        } catch (IOException e) {
            throw corrupt();
        }
    }

    private static BufferedImage decode(byte[] upload, SniffedFile type) {
        String format = switch (type.extension()) {
            case "jpg" -> "jpeg";
            case "png" -> "png";
            default -> "webp";
        };
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(upload))) {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(format);
            if (input == null || !readers.hasNext()) {
                throw ApiException.of(ApiErrorCode.UNSUPPORTED_MEDIA_TYPE, "file", "kitchen.file.unsupported");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                if ((long) reader.getWidth(0) * reader.getHeight(0) > MAX_PIXELS) {
                    throw ApiException.of(ApiErrorCode.VALIDATION_ERROR, "file", "kitchen.file.tooManyPixels", MAX_PIXELS / 1_000_000);
                }
                AtomicBoolean warned = new AtomicBoolean();
                reader.addIIOReadWarningListener((IIOReadWarningListener) (r, warning) -> warned.set(true));
                BufferedImage image = reader.read(0);
                if (image == null || warned.get()) {
                    throw corrupt();
                }
                return image;
            } finally {
                reader.dispose();
            }
        } catch (ApiException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw corrupt();
        }
    }

    /** WebP when libwebp is loaded (falls back on any encoder failure), else JPEG. */
    static Thumbnail encodeThumbnail(BufferedImage image) throws IOException {
        Optional<WebpEncoder> webp = WebpEncoder.instance();
        if (webp.isPresent()) {
            try {
                return new Thumbnail(webp.get().encode(image, WEBP_QUALITY), "webp", "image/webp");
            } catch (RuntimeException e) {
                log.warn("WebP thumbnail failed, using JPEG: {}", e.toString());
            }
        }
        return new Thumbnail(encode(image, "jpeg"), "jpg", "image/jpeg");
    }

    /** Longest edge at most {@code edge} (never upscaled). */
    private static BufferedImage scaled(BufferedImage source, int edge) {
        double scale = Math.min(1.0, (double) edge / Math.max(source.getWidth(), source.getHeight()));
        return flatten(source, Math.max(1, (int) Math.round(source.getWidth() * scale)), Math.max(1, (int) Math.round(source.getHeight() * scale)));
    }

    /** RGB copy on white (JPEG has no alpha), scaled to width × height. */
    private static BufferedImage flatten(BufferedImage source, int width, int height) {
        BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = output.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.drawImage(source, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return output;
    }

    private static byte[] encode(BufferedImage image, String format) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName(format).next();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(64 * 1024);
        try (ImageOutputStream output = ImageIO.createImageOutputStream(buffer)) {
            writer.setOutput(output);
            ImageWriteParam params = writer.getDefaultWriteParam();
            if ("jpeg".equals(format)) {
                params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                params.setCompressionQuality(JPEG_QUALITY);
            }
            writer.write(null, new IIOImage(image, null, null), params);
        } finally {
            writer.dispose();
        }
        return buffer.toByteArray();
    }

    /** Drops EXIF/XMP chunks, clears their VP8X flags and fixes the RIFF size; anything after the RIFF is discarded. */
    static byte[] stripWebpMetadata(byte[] webp) {
        ByteBuffer in = ByteBuffer.wrap(webp).order(ByteOrder.LITTLE_ENDIAN);
        long riffEnd = Math.min(webp.length, 8L + Integer.toUnsignedLong(in.getInt(4)));
        ByteArrayOutputStream out = new ByteArrayOutputStream(webp.length);
        out.write(webp, 0, 12);
        int position = 12;
        while (position + 8 <= riffEnd) {
            String fourcc = new String(webp, position, 4, java.nio.charset.StandardCharsets.US_ASCII);
            long size = Integer.toUnsignedLong(in.getInt(position + 4));
            long padded = size + (size & 1);
            if (position + 8 + size > riffEnd) {
                throw corrupt();
            }
            if (!WEBP_METADATA.contains(fourcc)) {
                int length = (int) Math.min(8 + padded, riffEnd - position);
                byte[] chunk = java.util.Arrays.copyOfRange(webp, position, position + length);
                if ("VP8X".equals(fourcc) && chunk.length > 8) {
                    chunk[8] &= (byte) ~0x0C;
                }
                out.write(chunk, 0, chunk.length);
            }
            position += (int) (8 + padded);
        }
        byte[] result = out.toByteArray();
        ByteBuffer.wrap(result).order(ByteOrder.LITTLE_ENDIAN).putInt(4, result.length - 8);
        return result;
    }

    private static ApiException corrupt() {
        return ApiException.of(ApiErrorCode.VALIDATION_ERROR, "file", "kitchen.file.corrupt");
    }
}
