package com.wishapp.domain;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for updating an existing wish.
 */
public record UpdateWishRequest(
    @NotBlank String name,
    String[] tags,
    @NotBlank String comment,
    String pictureBase64
) {
}
