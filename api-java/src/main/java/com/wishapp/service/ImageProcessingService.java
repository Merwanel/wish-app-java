package com.wishapp.service;

import com.sksamuel.scrimage.ImmutableImage;
import com.sksamuel.scrimage.webp.WebpWriter;
import java.util.Base64;
import org.springframework.stereotype.Service;

/**
 * Service for image processing operations.
 *
 * <p>Converts images to WebP format with resizing (200x300) and quality settings.</p>
 */
@Service
public class ImageProcessingService {

    private static final int TARGET_WIDTH = 200;
    private static final int TARGET_HEIGHT = 300;

    /**
     * Resize and convert base64 image to WebP.
     *
     * @param base64Image the base64-encoded image
     * @return base64-encoded WebP image
     */
    public String resizeAndConvertToWebP(String base64Image) {
        if (base64Image == null || base64Image.isEmpty()) {
            return null;
        }

        byte[] imageBytes = Base64.getDecoder().decode(base64Image);
        if (imageBytes.length == 0) {
            return null;
        }

        try {
            ImmutableImage image = ImmutableImage.loader().fromBytes(imageBytes);
            ImmutableImage resized = image.resizeTo(TARGET_WIDTH, TARGET_HEIGHT);
            WebpWriter writer = new WebpWriter();
            byte[] webpBytes = resized.bytes(writer);
            return Base64.getEncoder().encodeToString(webpBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to process image", e);
        }
    }

    /**
     * Convert base64 image to WebP without resizing.
     *
     * @param base64Image the base64-encoded image
     * @return base64-encoded WebP image
     */
    public String convertToWebP(String base64Image) {
        if (base64Image == null || base64Image.isEmpty()) {
            return null;
        }

        byte[] imageBytes = Base64.getDecoder().decode(base64Image);
        if (imageBytes.length == 0) {
            return null;
        }

        try {
            ImmutableImage image = ImmutableImage.loader().fromBytes(imageBytes);
            WebpWriter writer = new WebpWriter();
            byte[] webpBytes = image.bytes(writer);
            return Base64.getEncoder().encodeToString(webpBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert image to WebP", e);
        }
    }
}
