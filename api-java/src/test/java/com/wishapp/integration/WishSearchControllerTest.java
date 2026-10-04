package com.wishapp.integration;

import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.wishapp.apijava.ApiJavaApplication;
import com.wishapp.domain.Wish;
import com.wishapp.repository.WishRepository;
import com.wishapp.service.WishEtlService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * {@code GET /wishes/search} against Testcontainers (Postgres, Redis, Elasticsearch).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = ApiJavaApplication.class)
class WishSearchControllerTest {

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        WishAppTestcontainers.registerProperties(registry);
    }

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    WishRepository wishRepository;

    @Autowired
    WishEtlService wishEtlService;

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Autowired
    ElasticsearchClient elasticsearchClient;

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

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void fuzzySearch_findsTypoAndHighlights() {
        saveAndSync("Zelda Breath of the Wild", new String[] {"gaming", "nintendo"},
            "Best game ever", new byte[] {10, 20, 30});

        ResponseEntity<Map<String, Object>> res = restTemplate.exchange(
            url("/wishes/search?q=zeldq"),
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<>() {});

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = res.getBody();
        assertThat(body).isNotNull();
        assertThat(((Number) body.get("total")).longValue()).isEqualTo(1);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> wishes = (List<Map<String, Object>>) body.get("wishes");
        assertThat(wishes).hasSize(1);
        assertThat((String) wishes.get(0).get("highlightedName")).contains("<mark>");
        assertThat((String) wishes.get(0).get("picture")).isNotBlank();
    }

    @Test
    void emptyQuery_browseMatchAllWithTagAggregations() {
        saveAndSync("Alpha", new String[] {"gaming"}, "a", new byte[] {1});
        saveAndSync("Beta", new String[] {"gaming", "rpg"}, "b", new byte[] {2});

        ResponseEntity<Map<String, Object>> res = restTemplate.exchange(
            url("/wishes/search"),
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<>() {});

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = res.getBody();
        assertThat(((Number) body.get("total")).longValue()).isEqualTo(2);

        @SuppressWarnings("unchecked")
        Map<String, Object> aggs = (Map<String, Object>) body.get("aggregations");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tags = (List<Map<String, Object>>) aggs.get("tags");
        assertThat(tags.stream().map(t -> t.get("key"))).contains("gaming");
    }

    @Test
    void tagFilter_restrictsResults() {
        saveAndSync("Zelda", new String[] {"gaming"}, "game", new byte[] {1});
        saveAndSync("Cookbook", new String[] {"cooking"}, "food", new byte[] {2});

        ResponseEntity<Map<String, Object>> res = restTemplate.exchange(
            url("/wishes/search?tag=gaming"),
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<>() {});

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> wishes = (List<Map<String, Object>>) res.getBody().get("wishes");
        assertThat(wishes).hasSize(1);
        assertThat(wishes.get(0).get("name")).isEqualTo("Zelda");
    }

    @Test
    void limitAndOffset_clampAndPage() {
        saveAndSync("Alpha", new String[] {"t"}, "a", new byte[] {1});
        saveAndSync("Beta", new String[] {"t"}, "b", new byte[] {2});
        saveAndSync("Gamma", new String[] {"t"}, "c", new byte[] {3});

        ResponseEntity<Map<String, Object>> page0 = restTemplate.exchange(
            url("/wishes/search?limit=0&offset=-1"),
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<>() {});
        ResponseEntity<Map<String, Object>> expectedFirst = restTemplate.exchange(
            url("/wishes/search?limit=1&offset=0"),
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<>() {});
        ResponseEntity<Map<String, Object>> page1 = restTemplate.exchange(
            url("/wishes/search?limit=1&offset=1"),
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<>() {});

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> page0Wishes =
            (List<Map<String, Object>>) page0.getBody().get("wishes");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> firstWishes =
            (List<Map<String, Object>>) expectedFirst.getBody().get("wishes");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> page1Wishes =
            (List<Map<String, Object>>) page1.getBody().get("wishes");

        assertThat(page0Wishes).hasSize(1);
        assertThat(page0Wishes.get(0).get("name")).isEqualTo(firstWishes.get(0).get("name"));
        assertThat(((Number) page0.getBody().get("total")).longValue()).isEqualTo(3);
        assertThat(page1Wishes).hasSize(1);
        assertThat(page1Wishes.get(0).get("name")).isNotEqualTo(page0Wishes.get(0).get("name"));
        assertThat(((Number) page1.getBody().get("total")).longValue()).isEqualTo(3);
    }

    @Test
    void queryAndTagTogether_excludesTextMatchWithWrongTag() {
        saveAndSync("Zelda", new String[] {"gaming"}, "game", new byte[] {1});
        saveAndSync("Zelda cookbook", new String[] {"cooking"}, "food", new byte[] {2});

        ResponseEntity<Map<String, Object>> res = restTemplate.exchange(
            url("/wishes/search?q=zelda&tag=cooking"),
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<>() {});

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> wishes = (List<Map<String, Object>>) res.getBody().get("wishes");
        assertThat(wishes).hasSize(1);
        assertThat(wishes.get(0).get("name")).isEqualTo("Zelda cookbook");
        assertThat(((Number) res.getBody().get("total")).longValue()).isEqualTo(1);
    }

    @Test
    void softDeletedInPostgresButStillInElasticsearch_isOmitted() throws Exception {
        Wish wish = saveAndSync("Ghost", new String[] {"tag"}, "gone", new byte[] {1});
        wish.setDeletedAt(Instant.now());
        wishRepository.save(wish);
        // Intentionally do not ETL again: ES still has the document

        ResponseEntity<Map<String, Object>> res = restTemplate.exchange(
            url("/wishes/search?q=Ghost"),
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<>() {});

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> wishes = (List<Map<String, Object>>) res.getBody().get("wishes");
        assertThat(wishes).isEmpty();
        assertThat(elasticsearchClient.exists(e -> e.index("wishes").id(String.valueOf(wish.getId())))
            .value()).isTrue();
    }

    @Test
    void internalEtlSync_returnsProcessedCount() {
        saveWish("SyncMe", new String[] {"tag"}, "c", new byte[] {1});

        ResponseEntity<Map<String, Object>> res = restTemplate.exchange(
            url("/internal/etl/sync"),
            HttpMethod.POST,
            null,
            new ParameterizedTypeReference<>() {});

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) res.getBody().get("processed")).intValue()).isGreaterThanOrEqualTo(1);
    }

    private Wish saveAndSync(String name, String[] tags, String comment, byte[] picture) {
        Wish wish = saveWish(name, tags, comment, picture);
        wishEtlService.sync(true);
        return wish;
    }

    private Wish saveWish(String name, String[] tags, String comment, byte[] picture) {
        Wish wish = new Wish();
        wish.setName(name);
        wish.setTags(tags);
        wish.setComment(comment);
        wish.setPicture(picture);
        return wishRepository.save(wish);
    }
}
