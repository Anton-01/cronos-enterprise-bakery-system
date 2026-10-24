package com.ninsky.cronos.mail.infrastructure;

import com.ninsky.cronos.infrastructure.exception.ApiErrorCode;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import com.ninsky.cronos.kitchen.recipe.file.ImageSanitizer;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;

/**
 * Email images (§4.5): the type comes from magic bytes, never from the extension or the client's {@code Content-Type}
 * (SVG and anything else are refused: most mail clients do not render SVG, and it is an XSS vector). Limits: 2 MB and
 * 4000 × 4000 px, checked from the header before decoding. Metadata is removed and polyglot payloads dropped:
 * JPEG and PNG are decoded and re-encoded; WebP (no JDK writer) loses its EXIF/XMP chunks and anything after the RIFF;
 * GIF keeps its frames and loop but loses comments, foreign application blocks and anything after the trailer.
 */
public final class EmailImageSanitizer {

    public static final int MAX_BYTES = 2 * 1024 * 1024;
    public static final int MAX_EDGE = 4000;
    private static final float JPEG_QUALITY = 0.9f;
    /** Application extensions that drive animation; every other one (XMP, ICC…) is metadata. */
    private static final Set<String> ANIMATION_EXTENSIONS = Set.of("NETSCAPE2.0", "ANIMEXTS1.0");

    /** An accepted image type. */
    public enum Type {
        PNG("image/png", "png"),
        JPEG("image/jpeg", "jpg"),
        GIF("image/gif", "gif"),
        WEBP("image/webp", "webp");

        private final String contentType;
        private final String extension;

        Type(String contentType, String extension) {
            this.contentType = contentType;
            this.extension = extension;
        }

        public String contentType() {
            return contentType;
        }

        public String extension() {
            return extension;
        }

        String format() {
            return this == JPEG ? "jpeg" : name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** The cleaned image. */
    public record Result(byte[] bytes, Type type, int width, int height) {
    }

    static {
        ImageIO.setUseCache(false);
        ImageIO.scanForPlugins();
    }

    private EmailImageSanitizer() {
    }

    /**
     * @throws ApiException ASSET_TOO_LARGE, ASSET_INVALID_TYPE (also for a corrupt file), ASSET_DIMENSIONS
     */
    public static Result process(byte[] upload) {
        if (upload == null || upload.length == 0) {
            throw invalid();
        }
        if (upload.length > MAX_BYTES) {
            throw ApiException.of(ApiErrorCode.ASSET_TOO_LARGE, "file", "mail.asset.tooLarge", MAX_BYTES / (1024 * 1024));
        }
        Type type = sniff(upload).orElseThrow(EmailImageSanitizer::invalid);
        BufferedImage decoded = decode(upload, type);
        try {
            byte[] clean = switch (type) {
                case JPEG -> encode(opaque(decoded), "jpeg");
                case PNG -> encode(decoded, "png");
                case WEBP -> ImageSanitizer.stripWebpMetadata(upload);
                case GIF -> stripGif(upload);
            };
            return new Result(clean, type, decoded.getWidth(), decoded.getHeight());
        } catch (IOException | RuntimeException e) {
            if (e instanceof ApiException api) {
                throw api;
            }
            throw invalid();
        }
    }

    /** PNG, JPEG, GIF or WebP by signature; empty for anything else (SVG, HTML renamed to .png…). */
    public static Optional<Type> sniff(byte[] data) {
        if (starts(data, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of(Type.PNG);
        }
        if (starts(data, 0, 0xFF, 0xD8, 0xFF)) {
            return Optional.of(Type.JPEG);
        }
        if (starts(data, 0, 'G', 'I', 'F', '8', '7', 'a') || starts(data, 0, 'G', 'I', 'F', '8', '9', 'a')) {
            return Optional.of(Type.GIF);
        }
        if (starts(data, 0, 'R', 'I', 'F', 'F') && starts(data, 8, 'W', 'E', 'B', 'P')) {
            return Optional.of(Type.WEBP);
        }
        return Optional.empty();
    }

    private static BufferedImage decode(byte[] upload, Type type) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(upload))) {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(type.format());
            if (input == null || !readers.hasNext()) {
                throw invalid();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, false, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width > MAX_EDGE || height > MAX_EDGE) {
                    throw ApiException.of(ApiErrorCode.ASSET_DIMENSIONS, "file", "mail.asset.dimensions", MAX_EDGE);
                }
                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw invalid();
                }
                return image;
            } finally {
                reader.dispose();
            }
        } catch (ApiException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw invalid();
        }
    }

    /** RGB on white (JPEG has no alpha). */
    private static BufferedImage opaque(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_RGB || source.getType() == BufferedImage.TYPE_3BYTE_BGR) {
            return source;
        }
        BufferedImage output = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = output.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, source.getWidth(), source.getHeight());
            g.drawImage(source, 0, 0, null);
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

    /**
     * Copies a GIF block by block: header, screen descriptor, color tables, images, graphic-control and animation
     * extensions are kept; comment extensions and other application extensions are dropped; parsing stops at the trailer.
     */
    static byte[] stripGif(byte[] gif) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(gif.length);
        int position = 13;
        require(gif, position);
        out.write(gif, 0, 13);
        if ((gif[10] & 0x80) != 0) {
            int table = 3 << ((gif[10] & 0x07) + 1);
            require(gif, position + table);
            out.write(gif, position, table);
            position += table;
        }
        while (true) {
            require(gif, position + 1);
            int introducer = gif[position] & 0xFF;
            if (introducer == 0x3B) {
                out.write(0x3B);
                return out.toByteArray();
            }
            if (introducer == 0x2C) {
                require(gif, position + 10);
                int start = position;
                int packed = gif[position + 9] & 0xFF;
                position += 10;
                if ((packed & 0x80) != 0) {
                    position += 3 << ((packed & 0x07) + 1);
                }
                position += 1; // LZW minimum code size
                position = skipSubBlocks(gif, position);
                out.write(gif, start, position - start);
            } else if (introducer == 0x21) {
                require(gif, position + 2);
                int label = gif[position + 1] & 0xFF;
                int start = position;
                int end = skipSubBlocks(gif, position + 2);
                boolean keep = switch (label) {
                    case 0xF9, 0x01 -> true; // graphic control, plain text
                    case 0xFF -> isAnimationExtension(gif, position + 2);
                    default -> false; // 0xFE comment and unknown extensions
                };
                if (keep) {
                    out.write(gif, start, end - start);
                }
                position = end;
            } else {
                throw invalid();
            }
        }
    }

    private static boolean isAnimationExtension(byte[] gif, int blockStart) {
        int size = gif[blockStart] & 0xFF;
        if (size != 11) {
            return false;
        }
        require(gif, blockStart + 12);
        return ANIMATION_EXTENSIONS.contains(new String(Arrays.copyOfRange(gif, blockStart + 1, blockStart + 12), StandardCharsets.US_ASCII));
    }

    /** Position after the sub-block chain starting at {@code position} (ends with a zero-length block). */
    private static int skipSubBlocks(byte[] gif, int position) {
        while (true) {
            require(gif, position + 1);
            int size = gif[position] & 0xFF;
            position += 1 + size;
            if (size == 0) {
                return position;
            }
        }
    }

    private static void require(byte[] data, int length) {
        if (data.length < length) {
            throw invalid();
        }
    }

    private static boolean starts(byte[] data, int offset, int... signature) {
        if (data.length < offset + signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((data[offset + i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }

    private static ApiException invalid() {
        return ApiException.of(ApiErrorCode.ASSET_INVALID_TYPE, "file", "mail.asset.invalidType");
    }
}
