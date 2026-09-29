package com.wishapp.elasticsearch;

import com.wishapp.domain.Wish;
import java.time.Instant;

/**
 * Elasticsearch document for a wish. Picture blobs are intentionally excluded.
 */
public record WishDocument(
    Long id,
    String name,
    String[] tags,
    String comment,
    Instant createdAt,
    Instant updatedAt
) {

    public static WishDocument fromEntity(Wish wish) {
        return new WishDocument(
            wish.getId(),
            wish.getName(),
            wish.getTags() != null ? wish.getTags() : new String[0],
            wish.getComment(),
            wish.getCreatedAt(),
            wish.getUpdatedAt()
        );
    }
}
