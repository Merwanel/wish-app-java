package com.wishapp.integration;

import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.wishapp.apijava.ApiJavaApplication;
import com.wishapp.domain.Wish;
import com.wishapp.repository.WishRepository;
import com.wishapp.service.WishEtlService;
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
        assertThat(((Number) body.get("total")).longValue()).isGreaterThanOrEqualTo(1);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> wishes = (List<Map<String, Object>>) body.get("wishes");
        assertThat(wishes).isNotEmpty();
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

    private void saveAndSync(String name, String[] tags, String comment, byte[] picture) {
        Wish wish = new Wish();
        wish.setName(name);
        wish.setTags(tags);
        wish.setComment(comment);
        wish.setPicture(picture);
        wishRepository.save(wish);
        wishEtlService.sync(true);
    }
}
