package com.wishapp.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Scheduling is on by default in production. Tests set
 * {@code spring.task.scheduling.enabled=false} so {@code @Scheduled} ETL
 * does not race with Elasticsearch cleanup in {@code @BeforeEach}.
 *
 * <p>Note: {@code spring.task.scheduling.enabled} alone does not disable
 * methods registered via {@code @EnableScheduling} on the application class.</p>
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "spring.task.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
