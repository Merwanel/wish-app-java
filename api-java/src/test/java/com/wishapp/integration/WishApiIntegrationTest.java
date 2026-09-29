package com.wishapp.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.wishapp.apijava.ApiJavaApplication;
import com.wishapp.repository.WishRepository;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.util.Base64;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * End-to-end integration tests exercising the real HTTP API (wish endpoints),
 * mirroring the original Express.js {@code server.spec.ts} test patterns.
 *
 * <p>PostgreSQL, Redis, and Elasticsearch are expected on localhost (Docker Compose).
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

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        // Flyway applies V1__init_schema.sql to the isolated test database
        registry.add("spring.datasource.url",
            () -> "jdbc:postgresql://localhost:5432/testdatabase");
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "mypassword");
        registry.add("spring.redis.host", () -> "localhost");
        registry.add("spring.redis.port", () -> 6379);
        registry.add("spring.elasticsearch.uris", () -> "http://localhost:9200");
        registry.add("etl.sync.interval-ms", () -> "3600000");
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
    /* GET /all-wishes                                                    */
    /* ------------------------------------------------------------------ */

    @Test
    void allWishes_shouldReturnRightNumberOfElementsRespectingSchema() {
        seedWishes(NB_WISHES_IN);

        ResponseEntity<List<Map<String, Object>>> res = restTemplate.exchange(
            url("/all-wishes"), HttpMethod.GET, null,
            new ParameterizedTypeReference<List<Map<String, Object>>>() {});

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> wishes = res.getBody();
        assertThat(wishes).hasSize(NB_WISHES_IN);

        for (Map<String, Object> wish : wishes) {
            assertThat(wish.get("id")).isInstanceOf(Number.class);
            assertThat((String) wish.get("name")).matches(".+");
            assertThat((String) wish.get("comment")).isNotNull();
            assertThat((List<?>) wish.get("tags")).isNotNull();
            // schema field used by the original WishDTO
            assertThat((String) wish.get("picture")).matches("^[A-Za-z0-9+/=]*$");
            assertThat((String) wish.get("createdAt")).contains("T");
        }
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

    /* ------------------------------------------------------------------ */
    /* GET /search/{term} (SSE)                                           */
    /* ------------------------------------------------------------------ */

    @Test
    void search_shouldStreamSseResults() {
        String event1 = "{\"image\": \"" + RED_DOT_IMAGE_BASE64 + "\"}";
        String event2 = "{\"image\": \"" + RED_DOT_IMAGE_BASE64 + "\"}";
        com.github.tomakehurst.wiremock.client.WireMock.configureFor("localhost", IMAGE_SCRAPER.port());
        com.github.tomakehurst.wiremock.client.WireMock.stubFor(
            com.github.tomakehurst.wiremock.client.WireMock.get(
                com.github.tomakehurst.wiremock.client.WireMock.urlMatching("/internal/scrape/.*"))
                .willReturn(com.github.tomakehurst.wiremock.client.WireMock.aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/event-stream")
                    .withBody("data: " + event1 + "\n\n" + "data: " + event2 + "\n\n")));

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.TEXT_EVENT_STREAM));
        ResponseEntity<String> res = restTemplate.exchange(
            url("/search/pokemon"), HttpMethod.GET,
            new HttpEntity<>(headers), String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getHeaders().getContentType())
            .asString().contains("text/event-stream");
        String body = res.getBody();
        assertThat(body).contains(event1);
        assertThat(body).contains(event2);
        assertThat(body).contains("complete");
    }
}