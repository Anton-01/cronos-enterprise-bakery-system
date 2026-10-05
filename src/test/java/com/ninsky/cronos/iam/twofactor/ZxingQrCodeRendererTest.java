package com.ninsky.cronos.iam.twofactor;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class ZxingQrCodeRendererTest {

    private static final String URI = "otpauth://totp/Cronos:admin%40cronos.com?secret=JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP"
            + "&issuer=Cronos&algorithm=SHA1&digits=6&period=30";

    @Test
    void rendersAScannable240PxPngDataUri() throws Exception {
        String dataUri = new ZxingQrCodeRenderer().pngDataUri(URI);
        assertThat(dataUri).startsWith("data:image/png;base64,");

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(
                Base64.getDecoder().decode(dataUri.substring("data:image/png;base64,".length()))));
        assertThat(image.getWidth()).isEqualTo(240);
        assertThat(image.getHeight()).isEqualTo(240);
        int[] pixels = image.getRGB(0, 0, 240, 240, null, 0, 240);
        String decoded = new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(240, 240, pixels))))
                .getText();
        assertThat(decoded).isEqualTo(URI);
    }
}
