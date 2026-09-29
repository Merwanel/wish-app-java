package com.wishapp.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wishapp.domain.Wish;
import com.wishapp.repository.WishRepository;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-shot demo seed, mirroring Express {@code initDb}: runs only when
 * {@code init_marker} is empty, and is skipped for the test database.
 */
@Component
public class WishSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(WishSeedRunner.class);
    private static final String SEED_JSON = "db/seed/init-data.json";
    private static final String SEED_IMAGE_DIR = "db/seed/";

    private final WishRepository wishRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Environment environment;

    public WishSeedRunner(
        WishRepository wishRepository,
        JdbcTemplate jdbcTemplate,
        ObjectMapper objectMapper,
        Environment environment
    ) {
        this.wishRepository = wishRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.environment = environment;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        if (shouldSkip()) {
            log.info("Skipping wish seed (test environment)");
            return;
        }
        Integer markerCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM init_marker", Integer.class);
        if (markerCount != null && markerCount > 0) {
            log.info("Database already seeded (init_marker present); skipping");
            return;
        }

        List<SeedWish> seeds = loadSeedData();
        log.info("Seeding {} wishes from classpath {}", seeds.size(), SEED_JSON);

        for (SeedWish seed : seeds) {
            Wish wish = new Wish();
            wish.setName(seed.name());
            wish.setComment(seed.comment());
            wish.setTags(seed.tags() != null ? seed.tags().toArray(String[]::new) : new String[0]);
            wish.setCreatedAt(Instant.parse(seed.createdAt()));
            wish.setPicture(loadImage(seed.imageFile()));
            wishRepository.save(wish);
        }

        jdbcTemplate.update("INSERT INTO init_marker DEFAULT VALUES");
        log.info("Wish seed completed ({} rows)", seeds.size());
    }

    private boolean shouldSkip() {
        String url = environment.getProperty("spring.datasource.url", "");
        return url.contains("testdatabase")
            || environment.matchesProfiles("test");
    }

    private List<SeedWish> loadSeedData() throws IOException {
        try (InputStream in = new ClassPathResource(SEED_JSON).getInputStream()) {
            return objectMapper.readValue(in, new TypeReference<>() {});
        }
    }

    private byte[] loadImage(String imageFile) throws IOException {
        ClassPathResource resource = new ClassPathResource(SEED_IMAGE_DIR + imageFile);
        try (InputStream in = resource.getInputStream()) {
            return in.readAllBytes();
        }
    }

    private record SeedWish(
        String name,
        String comment,
        String createdAt,
        List<String> tags,
        String imageFile
    ) {}
}
