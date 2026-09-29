package com.wishapp.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import com.wishapp.domain.Wish;
import com.wishapp.elasticsearch.WishDocument;
import com.wishapp.repository.WishRepository;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Delta ETL: Postgres → Elasticsearch, bookmarked in Redis as {@code etl:last_sync}.
 *
 * <p>Bookmark is {@code max(updatedAt)} of processed rows (never wall-clock NOW()).</p>
 */
@Service
public class WishEtlService {

    private static final Logger log = LoggerFactory.getLogger(WishEtlService.class);
    private static final Instant EPOCH = Instant.parse("1970-01-01T00:00:00Z");

    private final WishRepository wishRepository;
    private final ElasticsearchClient elasticsearchClient;
    private final StringRedisTemplate stringRedisTemplate;
    private final String indexName;
    private final String bookmarkKey;

    public WishEtlService(
            WishRepository wishRepository,
            ElasticsearchClient elasticsearchClient,
            StringRedisTemplate stringRedisTemplate,
            @Value("${elasticsearch.index:wishes}") String indexName,
            @Value("${etl.sync.bookmark-key:etl:last_sync}") String bookmarkKey) {
        this.wishRepository = wishRepository;
        this.elasticsearchClient = elasticsearchClient;
        this.stringRedisTemplate = stringRedisTemplate;
        this.indexName = indexName;
        this.bookmarkKey = bookmarkKey;
    }

    /**
     * Scheduled delta tick — no forced refresh (eventual consistency within interval).
     */
    @Scheduled(fixedDelayString = "${etl.sync.interval-ms:5000}")
    public void scheduledSync() {
        sync(false);
    }

    /**
     * Run a delta sync batch.
     *
     * @param refreshImmediately when true (write path / tests), force ES refresh so docs are searchable
     *                           before the caller returns
     * @return number of rows processed
     */
    public int sync(boolean refreshImmediately) {
        Instant lastSync = readBookmark();
        List<Wish> deltas = wishRepository.findByUpdatedAtGreaterThanEqual(lastSync);

        if (deltas.isEmpty()) {
            log.debug("ETL: no deltas since {}", lastSync);
            return 0;
        }

        BulkRequest.Builder bulk = new BulkRequest.Builder().index(indexName);
        if (refreshImmediately) {
            bulk.refresh(Refresh.True);
        }

        Instant maxUpdatedAt = lastSync;
        for (Wish wish : deltas) {
            String docId = String.valueOf(wish.getId());
            if (wish.getDeletedAt() != null) {
                bulk.operations(op -> op.delete(d -> d.index(indexName).id(docId)));
            } else {
                WishDocument doc = WishDocument.fromEntity(wish);
                bulk.operations(op -> op.index(idx -> idx
                    .index(indexName)
                    .id(docId)
                    .document(doc)));
            }
            if (wish.getUpdatedAt() != null && wish.getUpdatedAt().isAfter(maxUpdatedAt)) {
                maxUpdatedAt = wish.getUpdatedAt();
            }
        }

        try {
            BulkResponse response = elasticsearchClient.bulk(bulk.build());
            if (response.errors()) {
                log.error("ETL bulk had item errors (bookmark not advanced)");
                response.items().stream()
                    .filter(item -> item.error() != null)
                    .forEach(item -> log.error("ETL item error id={} type={} reason={}",
                        item.id(),
                        item.error().type(),
                        item.error().reason()));
                return 0;
            }
            // Truncate to micros to match PostgreSQL timestamptz precision
            writeBookmark(maxUpdatedAt.truncatedTo(java.time.temporal.ChronoUnit.MICROS));
            log.info("ETL synced {} wish(es); bookmark → {}", deltas.size(), maxUpdatedAt);
            return deltas.size();
        } catch (Exception e) {
            log.error("ETL sync failed; bookmark left at {}", lastSync, e);
            throw new IllegalStateException("ETL sync failed", e);
        }
    }

    private Instant readBookmark() {
        String raw = stringRedisTemplate.opsForValue().get(bookmarkKey);
        if (raw == null || raw.isBlank()) {
            return EPOCH;
        }
        return Instant.parse(raw);
    }

    private void writeBookmark(Instant timestamp) {
        stringRedisTemplate.opsForValue().set(bookmarkKey, timestamp.toString());
    }
}
