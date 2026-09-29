package com.wishapp.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.HighlightField;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.wishapp.domain.Wish;
import com.wishapp.domain.WishDTO;
import com.wishapp.domain.WishSearchResponse;
import com.wishapp.domain.WishSearchResponse.TagBucket;
import com.wishapp.domain.WishSearchResponse.WishSearchAggregations;
import com.wishapp.domain.WishSearchResponse.WishSearchHit;
import com.wishapp.elasticsearch.WishDocument;
import com.wishapp.repository.WishRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Elasticsearch-backed wish search with Postgres hydration for pictures.
 */
@Service
public class WishSearchService {

    private final ElasticsearchClient elasticsearchClient;
    private final WishRepository wishRepository;
    private final String indexName;

    public WishSearchService(
            ElasticsearchClient elasticsearchClient,
            WishRepository wishRepository,
            @Value("${elasticsearch.index:wishes}") String indexName) {
        this.elasticsearchClient = elasticsearchClient;
        this.wishRepository = wishRepository;
        this.indexName = indexName;
    }

    public WishSearchResponse search(String q, String tag, int limit, int offset) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        int safeOffset = Math.max(0, offset);
        String queryText = q == null ? "" : q.trim();
        String tagFilter = tag == null || tag.isBlank() ? null : tag.trim();

        try {
            SearchResponse<WishDocument> response = elasticsearchClient.search(s -> {
                s.index(indexName)
                    .from(safeOffset)
                    .size(safeLimit)
                    .query(buildQuery(queryText, tagFilter))
                    .aggregations("tags", a -> a.terms(t -> t.field("tags.keyword").size(100)))
                    .highlight(h -> h
                        .preTags("<mark>")
                        .postTags("</mark>")
                        .fields("name", HighlightField.of(f -> f))
                        .fields("comment", HighlightField.of(f -> f)));

                if (queryText.isEmpty()) {
                    s.sort(so -> so.field(f -> f.field("createdAt").order(SortOrder.Desc)));
                }
                return s;
            }, WishDocument.class);

            List<Hit<WishDocument>> hits = response.hits().hits();
            List<Long> orderedIds = hits.stream()
                .map(Hit::id)
                .filter(Objects::nonNull)
                .map(Long::valueOf)
                .toList();

            Map<Long, Wish> byId = orderedIds.isEmpty()
                ? Map.of()
                : wishRepository.findByIdInAndDeletedAtIsNull(orderedIds).stream()
                    .collect(Collectors.toMap(Wish::getId, w -> w));

            Map<Long, Hit<WishDocument>> hitById = hits.stream()
                .filter(h -> h.id() != null)
                .collect(Collectors.toMap(h -> Long.valueOf(h.id()), h -> h, (a, b) -> a));

            List<WishSearchHit> wishes = new ArrayList<>();
            for (Long id : orderedIds) {
                Wish wish = byId.get(id);
                if (wish == null) {
                    continue;
                }
                WishDTO dto = WishDTO.fromEntity(wish);
                Hit<WishDocument> hit = hitById.get(id);
                String highlightedName = firstHighlight(hit, "name");
                String highlightedComment = firstHighlight(hit, "comment");
                wishes.add(new WishSearchHit(
                    dto.id(),
                    dto.name(),
                    highlightedName,
                    dto.tags(),
                    dto.comment(),
                    highlightedComment,
                    dto.picture(),
                    dto.createdAt()
                ));
            }

            long total = response.hits().total() != null
                ? response.hits().total().value()
                : wishes.size();

            return new WishSearchResponse(total, wishes, buildTagAggregations(response));
        } catch (Exception e) {
            throw new IllegalStateException("Wish search failed", e);
        }
    }

    private static Query buildQuery(String queryText, String tagFilter) {
        Query main = queryText.isEmpty()
            ? Query.of(q -> q.matchAll(m -> m))
            : Query.of(q -> q.multiMatch(mm -> mm
                .query(queryText)
                .fields("name^3", "tags^2", "comment")
                .fuzziness("AUTO")));

        if (tagFilter == null) {
            return main;
        }

        Query tagQuery = Query.of(q -> q.term(t -> t.field("tags.keyword").value(tagFilter)));
        return Query.of(q -> q.bool(b -> b.must(main).filter(tagQuery)));
    }

    private static String firstHighlight(Hit<WishDocument> hit, String field) {
        if (hit == null || hit.highlight() == null) {
            return null;
        }
        List<String> fragments = hit.highlight().get(field);
        if (fragments == null || fragments.isEmpty()) {
            return null;
        }
        return fragments.get(0);
    }

    private static WishSearchAggregations buildTagAggregations(SearchResponse<WishDocument> response) {
        List<TagBucket> buckets = new ArrayList<>();
        Aggregate agg = response.aggregations() != null ? response.aggregations().get("tags") : null;
        if (agg != null && agg.isSterms()) {
            for (StringTermsBucket bucket : agg.sterms().buckets().array()) {
                buckets.add(new TagBucket(bucket.key().stringValue(), bucket.docCount()));
            }
        }
        return new WishSearchAggregations(buckets);
    }
}
