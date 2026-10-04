package com.wishapp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import com.wishapp.domain.Wish;
import com.wishapp.repository.WishRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * Mock-based unit tests for ETL bookmark safety on Elasticsearch failures.
 */
class WishEtlServiceUnitTest {

    private WishRepository wishRepository;
    private ElasticsearchClient elasticsearchClient;
    private StringRedisTemplate stringRedisTemplate;
    private ValueOperations<String, String> valueOperations;
    private WishEtlService wishEtlService;

    @BeforeEach
    void setUp() {
        wishRepository = mock(WishRepository.class);
        elasticsearchClient = mock(ElasticsearchClient.class);
        stringRedisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("etl:last_sync")).thenReturn(null);

        wishEtlService = new WishEtlService(
            wishRepository,
            elasticsearchClient,
            stringRedisTemplate,
            "wishes",
            "etl:last_sync"
        );
    }

    @Test
    void sync_emptyDeltas_returnsZeroAndDoesNotChangeBookmark() throws Exception {
        String existingBookmark = "2026-01-01T00:00:00Z";
        when(valueOperations.get("etl:last_sync")).thenReturn(existingBookmark);
        when(wishRepository.findByUpdatedAtGreaterThanEqual(any())).thenReturn(List.of());

        int processed = wishEtlService.sync(true);

        assertThat(processed).isEqualTo(0);
        verify(valueOperations, never()).set(anyString(), anyString());
        verify(elasticsearchClient, never()).bulk(any(BulkRequest.class));
    }

    @Test
    void sync_bulkItemErrors_returnsZeroAndDoesNotAdvanceBookmark() throws Exception {
        Wish wish = activeWish(1L, Instant.parse("2026-01-01T00:00:00Z"));
        when(wishRepository.findByUpdatedAtGreaterThanEqual(any())).thenReturn(List.of(wish));

        BulkResponse bulkResponse = mock(BulkResponse.class);
        BulkResponseItem item = mock(BulkResponseItem.class);
        when(bulkResponse.errors()).thenReturn(true);
        when(bulkResponse.items()).thenReturn(List.of(item));
        when(item.error()).thenReturn(null);
        when(elasticsearchClient.bulk(any(BulkRequest.class))).thenReturn(bulkResponse);

        int processed = wishEtlService.sync(true);

        assertThat(processed).isEqualTo(0);
        verify(valueOperations, never()).set(anyString(), anyString());
    }

    @Test
    void sync_elasticsearchThrows_leavesBookmarkUntouched() throws Exception {
        Wish wish = activeWish(2L, Instant.parse("2026-01-02T00:00:00Z"));
        when(wishRepository.findByUpdatedAtGreaterThanEqual(any())).thenReturn(List.of(wish));
        when(elasticsearchClient.bulk(any(BulkRequest.class)))
            .thenThrow(new RuntimeException("ES down"));

        assertThatThrownBy(() -> wishEtlService.sync(true))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("ETL sync failed");

        verify(valueOperations, never()).set(eq("etl:last_sync"), anyString());
    }

    @Test
    void scheduledSync_callsSyncWithoutForcedRefresh() {
        WishEtlService spyService = spy(wishEtlService);
        doReturn(0).when(spyService).sync(false);

        spyService.scheduledSync();

        verify(spyService).sync(false);
    }

    private static Wish activeWish(Long id, Instant updatedAt) {
        Wish wish = new Wish();
        wish.setId(id);
        wish.setName("Test");
        wish.setComment("comment");
        wish.setTags(new String[] {"tag"});
        wish.setPicture(new byte[] {1});
        wish.setCreatedAt(updatedAt);
        wish.setUpdatedAt(updatedAt);
        return wish;
    }
}
