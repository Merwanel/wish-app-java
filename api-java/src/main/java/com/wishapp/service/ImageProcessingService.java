package com.wishapp.service;

import com.sksamuel.scrimage.ImmutableImage;
import com.sksamuel.scrimage.webp.WebpWriter;
import java.util.Base64;
import org.springframework.stereotype.Service;

/**
 * Service for image processing operations.
 *
 * <p>Matches Express/sharp: cover-fit to 200x300 (center crop) and WebP quality 60.</p>
 */
@Service
public class ImageProcessingService {

    private static final int TARGET_WIDTH = 200;
    private static final int TARGET_HEIGHT = 300;
    private static final WebpWriter WEBP_WRITER = WebpWriter.DEFAULT.withQ(60);

    /**
     * Resize (cover) and convert base64 image to WebP.
     *
     * @param base64Image the base64-encoded image
     * @return base64-encoded WebP image
     */
    public String resizeAndConvertToWebP(String base64Image) {
        if (base64Image == null || base64Image.isEmpty()) {
            return null;
        }

        byte[] imageBytes = Base64.getDecoder().decode(base64Image);

        try {
            ImmutableImage image = ImmutableImage.loader().fromBytes(imageBytes);
            ImmutableImage resized = image.cover(TARGET_WIDTH, TARGET_HEIGHT);
            byte[] webpBytes = resized.bytes(WEBP_WRITER);
            return Base64.getEncoder().encodeToString(webpBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to process image", e);
        }
    }
}
