package com.wishapp.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Creates the {@code wishes} index with an explicit mapping on startup when missing.
 */
@Component
@Order(1)
public class WishIndexInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(WishIndexInitializer.class);

    private final ElasticsearchClient elasticsearchClient;
    private final String indexName;

    public WishIndexInitializer(
            ElasticsearchClient elasticsearchClient,
            @Value("${elasticsearch.index:wishes}") String indexName) {
        this.elasticsearchClient = elasticsearchClient;
        this.indexName = indexName;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        boolean exists = elasticsearchClient.indices()
            .exists(ExistsRequest.of(e -> e.index(indexName)))
            .value();

        if (exists) {
            log.info("Elasticsearch index '{}' already exists", indexName);
            return;
        }

        log.info("Creating Elasticsearch index '{}' with explicit mapping", indexName);
        elasticsearchClient.indices().create(c -> c
            .index(indexName)
            .mappings(m -> m
                .properties("id", p -> p.long_(l -> l))
                .properties("name", p -> p.text(t -> t
                    .fields("keyword", f -> f.keyword(k -> k))))
                .properties("tags", p -> p.text(t -> t
                    .fields("keyword", f -> f.keyword(k -> k))))
                .properties("comment", p -> p.text(t -> t))
                .properties("createdAt", p -> p.date(d -> d))
                .properties("updatedAt", p -> p.date(d -> d))
            )
        );
        log.info("Elasticsearch index '{}' created", indexName);
    }
}
