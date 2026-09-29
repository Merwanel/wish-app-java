package com.wishapp.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import java.util.List;

/**
 * Response for {@code GET /wishes/search}.
 */
public record WishSearchResponse(
    long total,
    List<WishSearchHit> wishes,
    WishSearchAggregations aggregations
) {

    public record WishSearchHit(
        Long id,
        String name,
        String highlightedName,
        String[] tags,
        String comment,
        String highlightedComment,
        String picture,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
        Instant createdAt
    ) {}

    public record WishSearchAggregations(List<TagBucket> tags) {}

    public record TagBucket(String key, long count) {}
}
