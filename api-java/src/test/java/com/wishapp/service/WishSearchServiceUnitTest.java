package com.wishapp.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.wishapp.elasticsearch.WishDocument;
import com.wishapp.repository.WishRepository;
import java.io.IOException;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Mock-based unit tests for Elasticsearch failure handling in wish search.
 */
class WishSearchServiceUnitTest {

    private ElasticsearchClient elasticsearchClient;
    private WishRepository wishRepository;
    private WishSearchService wishSearchService;

    @BeforeEach
    void setUp() {
        elasticsearchClient = mock(ElasticsearchClient.class);
        wishRepository = mock(WishRepository.class);
        wishSearchService = new WishSearchService(elasticsearchClient, wishRepository, "wishes");
    }

    @Test
    void search_elasticsearchThrows_wrapsAsIllegalStateException() throws Exception {
        when(elasticsearchClient.search(
                any(Function.class),
                eq(WishDocument.class)))
            .thenThrow(new IOException("ES down"));

        assertThatThrownBy(() -> wishSearchService.search("zelda", null, 50, 0))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Wish search failed");
    }
}
