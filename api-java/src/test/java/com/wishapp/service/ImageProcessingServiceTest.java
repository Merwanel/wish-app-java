package com.wishapp.service;

import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ImageProcessingService} input guards.
 */
public class ImageProcessingServiceTest {

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
}
