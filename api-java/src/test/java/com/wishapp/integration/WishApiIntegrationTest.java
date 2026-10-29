package com.wishapp.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.reset;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.wishapp.ApiJavaApplication;
import com.wishapp.repository.WishRepository;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * End-to-end integration tests exercising the real HTTP API (wish endpoints),
 * mirroring the original Express.js {@code server.spec.ts} test patterns.
 *
 * <p>PostgreSQL, Redis, and Elasticsearch come from {@link WishAppTestcontainers}.
 * WireMock stands in for the Node image-scraper microservice.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = ApiJavaApplication.class)
class WishApiIntegrationTest {

    private static final String RED_DOT_IMAGE_BASE64 =
        "iVBORw0KGgoAAAANSUhEUgAAAAUAAAAFCAYAAACNbyblAAAAHElEQVQI12P4//8/w38GIAXDIBKE0DHxgljNBAAO9TXL0Y4OHwAAAABJRU5ErkJggg==";

    private static final int NB_WISHES_IN = 10;

    static final WireMockServer IMAGE_SCRAPER = new WireMockServer(0);

    static {
        // Ensure the scraper stub server is bound before the Spring context initializes
        IMAGE_SCRAPER.start();
    }

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    WishRepository wishRepository;

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Autowired
    RedisTemplate<String, String> redisTemplate;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        WishAppTestcontainers.registerProperties(registry);
        // Override dummy scraper URL from shared helper with WireMock
        registry.add("image.scraper.url",
            () -> "http://localhost:" + IMAGE_SCRAPER.port());
    }

    @BeforeEach
    @AfterEach
    void cleanDatabase() {
        // Complete test isolation: wipe wishes between tests (same behaviour as Express spec)
        if (wishRepository != null) {
            wishRepository.deleteAll();
        }
    }

    @BeforeEach
    void resetWireMockAndSearchCache() {
        configureFor("localhost", IMAGE_SCRAPER.port());
        reset();
        if (stringRedisTemplate != null) {
            var keys = stringRedisTemplate.keys("search:results:*");
            if (keys != null && !keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
            }
        }
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }

    private void seedWishes(int count) {
        for (int i = 0; i < count; i++) {
            Map<String, Object> body = new TreeMap<>();
            body.put("name", "name for " + i);
            body.put("comment", "comment for " + i);
            body.put("tags", List.of("tag-for-" + i, "tag1", "tag2", "tag3", "tag4"));
            body.put("picture", RED_DOT_IMAGE_BASE64);
            ResponseEntity<String> res = restTemplate.exchange(
                url("/new-wish"), HttpMethod.POST,
                new HttpEntity<>(body, jsonHeaders()), String.class);
            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        }
    }

    private ResponseEntity<String> searchSse(String searchTerm) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.TEXT_EVENT_STREAM));
        var uri = UriComponentsBuilder.fromHttpUrl(url("/search/{term}"))
            .buildAndExpand(searchTerm)
            .encode()
            .toUri();
        return restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private static List<String> sseDataEvents(String body) {
        List<String> events = new ArrayList<>();
        if (body == null) {
            return events;
        }
        for (String block : body.split("\n\n")) {
            String trimmed = block.trim();
            if (trimmed.startsWith("data:")) {
                events.add(trimmed.substring("data:".length()).trim());
            }
        }
        return events;
    }

    private void stubScraperOk(String event1, String event2) {
        stubFor(get(urlMatching("/internal/scrape/.*"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/event-stream")
                .withBody("data: " + event1 + "\n\n" + "data: " + event2 + "\n\n")));
    }

    /* ------------------------------------------------------------------ */
    /* /status                                                            */
    /* ------------------------------------------------------------------ */

    @Test
    void status_shouldReturnOkWithSchema() {
        ResponseEntity<Map<String, Object>> res = restTemplate.exchange(
            url("/status"), HttpMethod.GET, null,
            new ParameterizedTypeReference<Map<String, Object>>() {});

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().get("status")).isEqualTo("ok");
        assertThat((String) res.getBody().get("timestamp")).endsWith("Z");
        assertThat((Number) res.getBody().get("uptime")).isNotNull();
    }

    /* ------------------------------------------------------------------ */
    /* POST /new-wish                                                     */
    /* ------------------------------------------------------------------ */

    @Test
    void newWish_shouldCreateWishInDatabaseAndReturn204() {
        long before = wishRepository.count();

        Map<String, Object> body = new TreeMap<>();
        body.put("name", "new-wish-test");
        body.put("comment", "new wish comment");
        body.put("tags", List.of("new wish tag"));
        body.put("picture", RED_DOT_IMAGE_BASE64);
        body.put("createdAt", "2026-09-29T08:00:00.000Z");

        ResponseEntity<String> res = restTemplate.exchange(
            url("/new-wish"), HttpMethod.POST,
            new HttpEntity<>(body, jsonHeaders()), String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(wishRepository.count()).isEqualTo(before + 1);
        // persisted picture was converted/resized to WebP -> different bytes from input
        assertThat(wishRepository.findAll().get(0).getPicture()).isNotEmpty();
    }

    @Test
    void newWish_withBlankName_shouldReturn400() {
        Map<String, Object> body = new TreeMap<>();
        body.put("name", "");
        body.put("comment", "comment");
        body.put("tags", List.of("t"));
        body.put("picture", RED_DOT_IMAGE_BASE64);

        ResponseEntity<String> res = restTemplate.exchange(
            url("/new-wish"), HttpMethod.POST,
            new HttpEntity<>(body, jsonHeaders()), String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(wishRepository.count()).isZero();
    }

    @Test
    void newWish_withNullTags_storesEmptyTags() {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "null-tags-wish");
        body.put("comment", "comment");
        body.put("tags", null);
        body.put("picture", RED_DOT_IMAGE_BASE64);

        ResponseEntity<String> res = restTemplate.exchange(
            url("/new-wish"), HttpMethod.POST,
            new HttpEntity<>(body, jsonHeaders()), String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(wishRepository.findAll().get(0).getTags()).isEmpty();
    }

    /* ------------------------------------------------------------------ */
    /* PATCH /update-wish                                                 */
    /* ------------------------------------------------------------------ */

    @Test
    void updateWish_shouldUpdateWishInDatabaseAndReturnUpdatedWish() {
        seedWishes(1);
        Long id = wishRepository.findAll().get(0).getId();
        String modifiedComment = "modified comment";

        Map<String, Object> body = new TreeMap<>();
        body.put("id", id);
        body.put("name", "name for 0");
        body.put("comment", modifiedComment);
        body.put("tags", List.of("tag-for-0", "added tag"));
        body.put("picture", RED_DOT_IMAGE_BASE64);

        ResponseEntity<Map<String, Object>> res = restTemplate.exchange(
            url("/update-wish"), HttpMethod.PATCH,
            new HttpEntity<>(body, jsonHeaders()),
            new ParameterizedTypeReference<Map<String, Object>>() {});

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().get("comment")).isEqualTo(modifiedComment);
        assertThat(wishRepository.findById(id).orElseThrow().getComment())
            .isEqualTo(modifiedComment);
    }

    @Test
    void updateWish_withoutPicture_leavesPictureUnchanged() {
        seedWishes(1);
        var existing = wishRepository.findAll().get(0);
        Long id = existing.getId();
        byte[] originalPicture = existing.getPicture().clone();

        Map<String, Object> body = new TreeMap<>();
        body.put("id", id);
        body.put("name", "name for 0");
        body.put("comment", "comment without new picture");
        body.put("tags", List.of("tag-for-0"));
        // picture intentionally omitted

        ResponseEntity<Map<String, Object>> res = restTemplate.exchange(
            url("/update-wish"), HttpMethod.PATCH,
            new HttpEntity<>(body, jsonHeaders()),
            new ParameterizedTypeReference<Map<String, Object>>() {});

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(wishRepository.findById(id).orElseThrow().getPicture())
            .isEqualTo(originalPicture);
    }

    @Test
    void updateWish_withNullTags_leavesTagsUnchanged() {
        seedWishes(1);
        var existing = wishRepository.findAll().get(0);
        Long id = existing.getId();
        String[] originalTags = existing.getTags().clone();

        Map<String, Object> body = new HashMap<>();
        body.put("id", id);
        body.put("name", "name for 0");
        body.put("comment", "comment with null tags");
        body.put("tags", null);
        body.put("picture", RED_DOT_IMAGE_BASE64);

        ResponseEntity<Map<String, Object>> res = restTemplate.exchange(
            url("/update-wish"), HttpMethod.PATCH,
            new HttpEntity<>(body, jsonHeaders()),
            new ParameterizedTypeReference<Map<String, Object>>() {});

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(wishRepository.findById(id).orElseThrow().getTags())
            .isEqualTo(originalTags);
    }

    @Test
    void updateWish_withoutId_shouldReturn400() {
        Map<String, Object> body = new TreeMap<>();
        body.put("name", "x");
        body.put("comment", "y");
        body.put("tags", List.of("t"));
        body.put("picture", RED_DOT_IMAGE_BASE64);

        ResponseEntity<String> res = restTemplate.exchange(
            url("/update-wish"), HttpMethod.PATCH,
            new HttpEntity<>(body, jsonHeaders()), String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateWish_withMissingId_shouldReturn404() {
        Map<String, Object> body = new TreeMap<>();
        body.put("id", 999999L);
        body.put("name", "x");
        body.put("comment", "y");
        body.put("picture", RED_DOT_IMAGE_BASE64);

        ResponseEntity<String> res = restTemplate.exchange(
            url("/update-wish"), HttpMethod.PATCH,
            new HttpEntity<>(body, jsonHeaders()), String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    /* ------------------------------------------------------------------ */
    /* DELETE /delete-wish/{id}                                           */
    /* ------------------------------------------------------------------ */

    @Test
    void deleteWish_shouldSoftDeleteWishAndReturn204() {
        seedWishes(NB_WISHES_IN);
        long before = wishRepository.count();
        Long id = wishRepository.findAll().get(0).getId();

        ResponseEntity<String> res = restTemplate.exchange(
            url("/delete-wish/" + id), HttpMethod.DELETE, null, String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        // row still exists (soft delete) but is flagged deleted
        assertThat(wishRepository.count()).isEqualTo(before);
        assertThat(wishRepository.findByIdAndDeletedAtIsNull(id)).isEmpty();
    }

    @Test
    void deleteWish_withNonNumericId_shouldReturn400() {
        ResponseEntity<String> res = restTemplate.exchange(
            url("/delete-wish/not-a-number"), HttpMethod.DELETE, null, String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void deleteWish_withUnknownId_shouldReturn404() {
        ResponseEntity<String> res = restTemplate.exchange(
            url("/delete-wish/999999"), HttpMethod.DELETE, null, String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    /* ------------------------------------------------------------------ */
    /* POST /convert-image                                                */
    /* ------------------------------------------------------------------ */

    @Test
    void convertImage_shouldReturnResizedWebpAsBase64() {
        Map<String, Object> body = new TreeMap<>();
        body.put("image_base64", RED_DOT_IMAGE_BASE64);

        ResponseEntity<byte[]> res = restTemplate.exchange(
            url("/convert-image"), HttpMethod.POST,
            new HttpEntity<>(body, jsonHeaders()), byte[].class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        byte[] bodyBytes = res.getBody();
        assertThat(bodyBytes).isNotNull();
        // Express returns res.json(base64String) -> a quoted base64 JSON string
        String json = new String(bodyBytes);
        String base64 = json.replace("\"", "").trim();
        assertThat(base64).matches("^[A-Za-z0-9+/=]+$");
        byte[] decoded = Base64.getDecoder().decode(base64);
        // WebP signature: RIFF....WEBP
        assertThat(decoded[0]).isEqualTo((byte) 'R');
        assertThat(decoded[1]).isEqualTo((byte) 'I');
        assertThat(decoded[2]).isEqualTo((byte) 'F');
        assertThat(decoded[3]).isEqualTo((byte) 'F');
    }

    @Test
    void convertImage_withEmptyImage_returnsOkWithEmptyBody() {
        Map<String, Object> body = new TreeMap<>();
        body.put("image_base64", "");

        ResponseEntity<String> res = restTemplate.exchange(
            url("/convert-image"), HttpMethod.POST,
            new HttpEntity<>(body, jsonHeaders()), String.class);

        // Current contract: null conversion result is serialized as an empty/null JSON body
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody() == null || res.getBody().isBlank() || "null".equals(res.getBody()))
            .isTrue();
    }

    /* ------------------------------------------------------------------ */
    /* GET /search/{term} (SSE)                                           */
    /* ------------------------------------------------------------------ */

    @Test
    void search_shouldStreamSseResults() {
        String event1 = "{\"image\": \"" + RED_DOT_IMAGE_BASE64 + "\"}";
        String event2 = "{\"image\": \"" + RED_DOT_IMAGE_BASE64 + "\"}";
        stubScraperOk(event1, event2);

        ResponseEntity<String> res = searchSse("pokemon");

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getHeaders().getContentType())
            .asString().contains("text/event-stream");
        List<String> events = sseDataEvents(res.getBody());
        assertThat(events).contains(event1, event2, "{\"type\": \"complete\"}");
    }

    @Test
    void search_cacheHit_replaysSameEventsWithoutCallingScraperAgain() {
        String event1 = "{\"image\": \"" + RED_DOT_IMAGE_BASE64 + "\"}";
        String event2 = "{\"image\": \"second\"}";
        stubScraperOk(event1, event2);

        ResponseEntity<String> miss = searchSse("pikachu");
        ResponseEntity<String> hit = searchSse("pikachu");

        assertThat(miss.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(hit.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(sseDataEvents(hit.getBody())).isEqualTo(sseDataEvents(miss.getBody()));
        assertThat(sseDataEvents(hit.getBody())).contains(
            event1, event2, "{\"type\": \"complete\"}");
        verify(1, getRequestedFor(urlMatching("/internal/scrape/.*")));
    }

    @Test
    void search_encodesTermWhenCallingScraper() {
        String event1 = "{\"image\": \"a\"}";
        stubFor(get(urlEqualTo("/internal/scrape/foo%20bar"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/event-stream")
                .withBody("data: " + event1 + "\n\n")));

        ResponseEntity<String> res = searchSse("foo bar");

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(sseDataEvents(res.getBody())).contains(event1, "{\"type\": \"complete\"}");
        verify(1, getRequestedFor(urlEqualTo("/internal/scrape/foo%20bar")));
    }

    @Test
    void search_scraperFailure_doesNotWriteRedisCache() {
        stubFor(get(urlMatching("/internal/scrape/.*"))
            .willReturn(aResponse().withStatus(500)));

        try {
            searchSse("fail-term");
        } catch (Exception ignored) {
            // completeWithError may surface as a client transport error
        }

        assertThat(stringRedisTemplate.opsForValue().get("search:results:fail-term")).isNull();
    }

    @Test
    void search_corruptRedisCache_fallsThroughToScraperAndStreamsValidSse() {
        String term = "corrupt-cache-term";
        // Write through the same RedisTemplate/serializer as SearchController so get()
        // succeeds, then ObjectMapper fails to parse the value as a List (corrupt cache).
        redisTemplate.opsForValue().set("search:results:" + term, "not-valid-json{{{");

        String event1 = "{\"image\": \"" + RED_DOT_IMAGE_BASE64 + "\"}";
        String event2 = "{\"image\": \"fallback\"}";
        stubScraperOk(event1, event2);

        ResponseEntity<String> res = searchSse(term);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getHeaders().getContentType())
            .asString().contains("text/event-stream");
        assertThat(sseDataEvents(res.getBody())).contains(event1, event2, "{\"type\": \"complete\"}");
        verify(1, getRequestedFor(urlMatching("/internal/scrape/.*")));
    }
}
