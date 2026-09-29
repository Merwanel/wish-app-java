package com.wishapp.apijava;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Wish App Java Backend - Spring Boot 3 Main Application
 *
 * <p>This application provides a REST API compatible with the legacy Express.js backend,
 * managing wishes with PostgreSQL persistence, Redis caching, and Elasticsearch indexing.</p>
 *
 * <p>The application class intentionally lives in {@code com.wishapp.apijava} while the rest
 * of the components live under {@code com.wishapp.*}. We therefore widen the component,
 * entity and repository scans to {@code com.wishapp} so controllers, services, repositories
 * and configuration beans are actually picked up.</p>
 *
 * @author Wish App Team
 */
@SpringBootApplication
@EnableJpaAuditing
@EnableScheduling
@ComponentScan(basePackages = "com.wishapp")
@EntityScan(basePackages = "com.wishapp.domain")
@EnableJpaRepositories(basePackages = "com.wishapp.repository")
public class ApiJavaApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiJavaApplication.class, args);
    }
}
