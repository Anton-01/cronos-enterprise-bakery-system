package com.ninsky.cronos.account.avatar.infrastructure;

import com.ninsky.cronos.account.avatar.application.port.ImageProcessor;
import com.ninsky.cronos.account.avatar.domain.AvatarPolicy;
import com.ninsky.cronos.account.avatar.domain.DetectedImageType;
import com.ninsky.cronos.account.avatar.domain.ImageDimensions;
import com.ninsky.cronos.account.avatar.domain.ProcessedImage;
import com.ninsky.cronos.account.shared.domain.AccountDomainError.ImageRejected;
import com.ninsky.cronos.account.shared.domain.AccountDomainError.ImageRejected.Reason;
import com.ninsky.cronos.account.shared.domain.AccountDomainException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * JDK ImageIO pipeline (+ TwelveMonkeys WebP reader via SPI). The upload is treated as hostile:
 * <ol>
 *   <li>type from magic bytes only;</li>
 *   <li>dimensions from the header, BEFORE decoding — rejects decompression bombs (&gt; 40 MP)
 *       and too-small images without allocating a pixel buffer;</li>
 *   <li>full decode; any reader warning (truncated / malformed stream) is a rejection, which also
 *       defeats half-valid polyglot files;</li>
 *   <li>center-crop to a square, downscale to ≤ 512 px, flatten alpha onto white, and re-encode as a
 *       fresh baseline JPEG (q=0.9) with no metadata — EXIF/GPS, ICC comments and any bytes appended
 *       after the image never survive.</li>
 * </ol>
 * Never logs image bytes.
 */
@Slf4j
@Component
public class ImageIoImageProcessor implements ImageProcessor {

    public ImageIoImageProcessor() {
        ImageIO.setUseCache(false);
        ImageIO.scanForPlugins();
    }

    @Override
    public ProcessedImage process(byte[] upload) {
        DetectedImageType type = DetectedImageType.sniff(Arrays.copyOf(upload, Math.min(upload.length, DetectedImageType.SNIFF_LENGTH)))
                .orElseThrow(() -> rejected(Reason.UNSUPPORTED_TYPE, "account.avatar.file.unsupportedType"));

        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(upload))) {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(type.formatName());
            if (input == null || !readers.hasNext()) {
                throw rejected(Reason.UNSUPPORTED_TYPE, "account.avatar.file.unsupportedType");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                ImageDimensions source = new ImageDimensions(reader.getWidth(0), reader.getHeight(0));
                if (source.exceedsPixels(AvatarPolicy.MAX_SOURCE_PIXELS)) {
                    throw rejected(Reason.INVALID, "account.avatar.file.tooManyPixels", AvatarPolicy.MAX_SOURCE_PIXELS / 1_000_000);
                }
                if (source.shorterEdge() < AvatarPolicy.MIN_EDGE_PX) {
                    throw rejected(Reason.INVALID, "account.avatar.file.tooSmall", AvatarPolicy.MIN_EDGE_PX);
                }

                AtomicBoolean warned = new AtomicBoolean();
                reader.addIIOReadWarningListener((IIOReadWarningListener) (r, warning) -> warned.set(true));
                BufferedImage decoded = reader.read(0);
                if (decoded == null || warned.get()) {
                    throw rejected(Reason.INVALID, "account.avatar.file.corrupt");
                }

                BufferedImage avatar = squareAndScale(decoded);
                byte[] jpeg = encodeJpeg(avatar);
                return new ProcessedImage(jpeg, new ImageDimensions(avatar.getWidth(), avatar.getHeight()), type);
            } finally {
                reader.dispose();
            }
        } catch (AccountDomainException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            // IIOException, IndexOutOfBounds, ColorSpace issues, DomainValidationException(0x0), ...
            log.info("Rejected undecodable {} avatar upload: {}", type.mediaType(), e.getClass().getSimpleName());
            throw rejected(Reason.INVALID, "account.avatar.file.corrupt");
        }
    }

    private static BufferedImage squareAndScale(BufferedImage source) {
        int edge = Math.min(source.getWidth(), source.getHeight());
        int x = (source.getWidth() - edge) / 2;
        int y = (source.getHeight() - edge) / 2;
        int target = Math.min(edge, AvatarPolicy.OUTPUT_MAX_EDGE_PX);

        BufferedImage output = new BufferedImage(target, target, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = output.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, target, target);
            g.drawImage(source, 0, 0, target, target, x, y, x + edge, y + edge, null);
        } finally {
            g.dispose();
        }
        return output;
    }

    private static byte[] encodeJpeg(BufferedImage image) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(64 * 1024);
        try (ImageOutputStream output = ImageIO.createImageOutputStream(buffer)) {
            writer.setOutput(output);
            ImageWriteParam params = writer.getDefaultWriteParam();
            params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            params.setCompressionQuality(AvatarPolicy.JPEG_QUALITY);
            writer.write(null, new IIOImage(image, null, null), params);
        } finally {
            writer.dispose();
        }
        return buffer.toByteArray();
    }

    private static AccountDomainException rejected(Reason reason, String messageKey, Object... args) {
        return new AccountDomainException(ImageRejected.of(reason, messageKey, args));
    }
}
