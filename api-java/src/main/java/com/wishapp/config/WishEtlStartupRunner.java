package com.wishapp.config;

import com.wishapp.service.WishEtlService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Runs an initial ETL sync after schema/index/seed so Angular browse is not empty on cold start.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnProperty(name = "etl.startup-sync.enabled", havingValue = "true", matchIfMissing = true)
public class WishEtlStartupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(WishEtlStartupRunner.class);

    private final WishEtlService wishEtlService;

    public WishEtlStartupRunner(WishEtlService wishEtlService) {
        this.wishEtlService = wishEtlService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            int n = wishEtlService.sync(true);
            log.info("Startup ETL synced {} wish(es)", n);
        } catch (Exception e) {
            log.warn("Startup ETL failed (scheduled tick will retry): {}", e.getMessage());
        }
    }
}
