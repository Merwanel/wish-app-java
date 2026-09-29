package com.wishapp.domain;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request DTO for updating an existing wish.
 * Uses Lombok Data instead of record to allow id field.
 */
@Data
public class UpdateWishRequest {
    private Long id;

    @NotBlank
    private String name;

    private String[] tags;

    @NotBlank
    private String comment;

    private String pictureBase64;
}
