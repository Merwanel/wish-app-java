package com.wishapp.apijava;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Wish App Java Backend - Spring Boot 3 Main Application
 *
 * <p>This application provides a REST API compatible with the legacy Express.js backend,
 * managing wishes with PostgreSQL persistence, Redis caching, and Elasticsearch indexing.</p>
 *
 * @author Wish App Team
 */
@SpringBootApplication
@EnableJpaAuditing
public class ApiJavaApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiJavaApplication.class, args);
    }
}
