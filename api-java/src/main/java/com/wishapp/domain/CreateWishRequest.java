package com.wishapp.domain;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for creating a new wish.
 */
public record CreateWishRequest(
    @NotBlank String name,
    String[] tags,
    @NotBlank String comment,
    @NotBlank String picture
) {
}
