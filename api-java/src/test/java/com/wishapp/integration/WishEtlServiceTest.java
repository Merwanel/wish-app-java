package com.wishapp.integration;

import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.wishapp.apijava.ApiJavaApplication;
import com.wishapp.domain.Wish;
import com.wishapp.repository.WishRepository;
import com.wishapp.service.WishEtlService;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * ETL pipeline against Compose infra (Postgres, Redis, Elasticsearch on localhost).
 * Asserts bookmark = max(updatedAt), soft-delete removal, and refresh semantics.
 */
@SpringBootTest(classes = ApiJavaApplication.class)
class WishEtlServiceTest {

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
            () -> "jdbc:postgresql://localhost:5432/testdatabase");
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "mypassword");
        registry.add("spring.redis.host", () -> "localhost");
        registry.add("spring.redis.port", () -> "6379");
        registry.add("spring.elasticsearch.uris", () -> "http://localhost:9200");
        registry.add("etl.sync.interval-ms", () -> "3600000");
        registry.add("image.scraper.url", () -> "http://127.0.0.1:9");
    }

    @Autowired
    WishEtlService wishEtlService;

    @Autowired
    WishRepository wishRepository;

    @Autowired
    ElasticsearchClient elasticsearchClient;

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void clean() throws Exception {
        wishRepository.deleteAll();
        stringRedisTemplate.delete("etl:last_sync");
        if (elasticsearchClient.indices().exists(e -> e.index("wishes")).value()) {
            elasticsearchClient.deleteByQuery(d -> d
                .index("wishes")
                .query(q -> q.matchAll(m -> m))
                .refresh(true));
        }
    }

    @Test
    void initialFullSync_indexesActiveWishesWithoutPicture() throws Exception {
        Wish a = saveWish("Zelda", "nintendo", "Great game");
        Wish b = saveWish("Switch", "hardware", "Console");

        int processed = wishEtlService.sync(true);
        assertThat(processed).isEqualTo(2);

        assertThat(elasticsearchClient.get(g -> g.index("wishes").id(String.valueOf(a.getId())),
            Object.class).found()).isTrue();
        assertThat(elasticsearchClient.get(g -> g.index("wishes").id(String.valueOf(b.getId())),
            Object.class).source().toString()).doesNotContain("picture");

        String bookmark = stringRedisTemplate.opsForValue().get("etl:last_sync");
        assertThat(bookmark).isNotBlank();
        Instant bookmarkInstant = Instant.parse(bookmark);
        Instant maxUpdated = a.getUpdatedAt().isAfter(b.getUpdatedAt()) ? a.getUpdatedAt() : b.getUpdatedAt();
        assertThat(bookmarkInstant.truncatedTo(java.time.temporal.ChronoUnit.MILLIS))
            .isEqualTo(maxUpdated.truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
    }

    @Test
    void deltaSync_updatesChangedWishAndAdvancesBookmarkToMaxUpdatedAt() throws Exception {
        Wish wish = saveWish("Zelda", "gaming", "comment");
        wishEtlService.sync(true);
        String firstBookmark = stringRedisTemplate.opsForValue().get("etl:last_sync");

        wish.setComment("updated comment");
        Wish updated = wishRepository.save(wish);
        Instant expected = updated.getUpdatedAt();
        Long wishId = updated.getId();

        int processed = wishEtlService.sync(true);
        assertThat(processed).isEqualTo(1);

        var doc = elasticsearchClient.get(g -> g.index("wishes").id(String.valueOf(wishId)),
            java.util.Map.class);
        assertThat(doc.source().get("comment")).isEqualTo("updated comment");
        assertThat(Instant.parse(stringRedisTemplate.opsForValue().get("etl:last_sync"))
                .truncatedTo(java.time.temporal.ChronoUnit.MILLIS))
            .isEqualTo(expected.truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
        assertThat(stringRedisTemplate.opsForValue().get("etl:last_sync"))
            .isNotEqualTo(firstBookmark);
    }

    @Test
    void softDelete_removesDocumentFromElasticsearch() throws Exception {
        Wish wish = saveWish("Ghost", "tag", "will delete");
        Long wishId = wish.getId();
        wishEtlService.sync(true);
        assertThat(elasticsearchClient.exists(e -> e.index("wishes").id(String.valueOf(wishId)))
            .value()).isTrue();

        wish.setDeletedAt(Instant.now());
        wishRepository.save(wish);
        wishEtlService.sync(true);

        assertThat(elasticsearchClient.exists(e -> e.index("wishes").id(String.valueOf(wishId)))
            .value()).isFalse();
    }

    @Test
    void writeTriggeredRefresh_makesWishImmediatelySearchable() throws Exception {
        saveWish("Immediate", "tag", "search me");
        wishEtlService.sync(true);

        var response = elasticsearchClient.search(s -> s
            .index("wishes")
            .query(q -> q.match(m -> m.field("name").query("Immediate"))),
            Object.class);
        assertThat(response.hits().total().value()).isEqualTo(1);
    }

    private Wish saveWish(String name, String tag, String comment) {
        Wish wish = new Wish();
        wish.setName(name);
        wish.setTags(new String[] {tag});
        wish.setComment(comment);
        wish.setPicture(new byte[] {1, 2, 3});
        return wishRepository.save(wish);
    }
}
