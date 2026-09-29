package com.wishapp.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import java.util.Arrays;

/**
 * DTO for Wish entity with base64-encoded picture for API responses.
 *
 * <p>The {@code picture} field mirrors the base64 {@code picture} field of the
 * original Express.js {@code WishDTO} schema.</p>
 */
public record WishDTO(
    Long id,
    String name,
    String[] tags,
    String comment,
    String picture,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    Instant createdAt
) {

    /**
     * Convert entity to DTO.
     *
     * @param entity the wish entity
     * @return the DTO
     */
    public static WishDTO fromEntity(Wish entity) {
        return new WishDTO(
            entity.getId(),
            entity.getName(),
            entity.getTags(),
            entity.getComment(),
            entity.getPicture() != null ? java.util.Base64.getEncoder().encodeToString(entity.getPicture()) : null,
            entity.getCreatedAt()
        );
    }
}
