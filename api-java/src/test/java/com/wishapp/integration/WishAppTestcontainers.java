package com.wishapp.integration;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared Testcontainers for ETL/search integration tests (Postgres + Redis + Elasticsearch).
 * Started once per JVM so suites stay self-contained without Compose.
 */
final class WishAppTestcontainers {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:17.5-alpine"))
            .withDatabaseName("testdatabase")
            .withUsername("postgres")
            .withPassword("mypassword");

    static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:8.2.2-alpine"))
            .withExposedPorts(6379);

    static final ElasticsearchContainer ELASTICSEARCH = new ElasticsearchContainer(
            DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:8.17.0"))
            .withEnv("xpack.security.enabled", "false")
            .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m");

    static {
        POSTGRES.start();
        REDIS.start();
        ELASTICSEARCH.start();
    }

    private WishAppTestcontainers() {}

    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.elasticsearch.uris",
            () -> "http://" + ELASTICSEARCH.getHttpHostAddress());
        registry.add("etl.sync.interval-ms", () -> "3600000");
        registry.add("image.scraper.url", () -> "http://127.0.0.1:9");
    }
}
