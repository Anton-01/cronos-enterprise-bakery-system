package com.ninsky.cronos.kitchen.recipe.file;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class WebpThumbnailTest {

    private static BufferedImage sample() {
        BufferedImage image = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.ORANGE);
        g.fillRect(0, 0, 320, 200);
        g.setColor(Color.BLUE);
        g.fillOval(60, 40, 120, 120);
        g.dispose();
        return image;
    }

    @Test
    void encodesWebpWhenLibwebpIsPresent() throws IOException {
        assumeTrue(WebpEncoder.instance().isPresent(), "libwebp not installed");
        ImageSanitizer.Thumbnail thumbnail = ImageSanitizer.encodeThumbnail(sample());

        assertThat(thumbnail.extension()).isEqualTo("webp");
        assertThat(thumbnail.mimeType()).isEqualTo("image/webp");
        assertThat(new String(Arrays.copyOfRange(thumbnail.bytes(), 0, 4), StandardCharsets.US_ASCII)).isEqualTo("RIFF");
        assertThat(new String(Arrays.copyOfRange(thumbnail.bytes(), 8, 12), StandardCharsets.US_ASCII)).isEqualTo("WEBP");
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(thumbnail.bytes()));
        assertThat(decoded).isNotNull();
        assertThat(decoded.getWidth()).isEqualTo(320);
        assertThat(decoded.getHeight()).isEqualTo(200);
    }

    @Test
    void fallsBackToJpegWithoutLibwebp() throws IOException {
        assumeTrue(WebpEncoder.instance().isEmpty(), "libwebp installed");
        ImageSanitizer.Thumbnail thumbnail = ImageSanitizer.encodeThumbnail(sample());

        assertThat(thumbnail.extension()).isEqualTo("jpg");
        assertThat(thumbnail.bytes()[0]).isEqualTo((byte) 0xFF);
    }
}
