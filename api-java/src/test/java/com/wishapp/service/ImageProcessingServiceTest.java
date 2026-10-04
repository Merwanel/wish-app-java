package com.wishapp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sksamuel.scrimage.ImmutableImage;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ImageProcessingService}.
 */
class ImageProcessingServiceTest {

    private ImageProcessingService imageProcessingService;

    @BeforeEach
    void setUp() {
        imageProcessingService = new ImageProcessingService();
    }

    @Test
    void resizeAndConvertToWebP_nullInput_returnsNull() {
        assertNull(imageProcessingService.resizeAndConvertToWebP(null));
    }

    @Test
    void resizeAndConvertToWebP_emptyInput_returnsNull() {
        assertNull(imageProcessingService.resizeAndConvertToWebP(""));
    }

    @Test
    void resizeAndConvertToWebP_validPng_returnsWebpAt200x300() throws Exception {
        String result = imageProcessingService.resizeAndConvertToWebP(pngBase64(400, 600, Color.RED));

        assertThat(result).isNotBlank();
        byte[] webp = Base64.getDecoder().decode(result);
        assertThat(webp).hasSizeGreaterThan(12);
        assertThat(new String(webp, 0, 4)).isEqualTo("RIFF");
        assertThat(new String(webp, 8, 4)).isEqualTo("WEBP");

        ImmutableImage loaded = ImmutableImage.loader().fromBytes(webp);
        assertThat(loaded.width).isEqualTo(200);
        assertThat(loaded.height).isEqualTo(300);
    }

    @Test
    void resizeAndConvertToWebP_largeImage_isCoverScaledNotPadded() throws Exception {
        // Wide image: cover(200,300) scales by height then center-crops width.
        String result = imageProcessingService.resizeAndConvertToWebP(pngBase64(900, 300, Color.BLUE));
        ImmutableImage loaded = ImmutableImage.loader().fromBytes(Base64.getDecoder().decode(result));

        assertThat(loaded.width).isEqualTo(200);
        assertThat(loaded.height).isEqualTo(300);
    }

    @Test
    void resizeAndConvertToWebP_validBase64ThatIsNotAnImage_throwsRuntimeException() {
        String notAnImage = Base64.getEncoder().encodeToString("hello".getBytes());

        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> imageProcessingService.resizeAndConvertToWebP(notAnImage));

        assertThat(ex.getMessage()).contains("Failed to process image");
    }

    private static String pngBase64(int width, int height, Color color) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, width, height);
        g.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return Base64.getEncoder().encodeToString(out.toByteArray());
    }
}
