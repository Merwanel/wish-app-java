package com.wishapp.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Official elasticsearch-java client (no Spring Data Elasticsearch repositories).
 */
@Configuration
public class ElasticsearchConfig {

    @Bean(destroyMethod = "close")
    public RestClient elasticsearchRestClient(
            @Value("${spring.elasticsearch.uris:http://localhost:9200}") String uris) {
        return RestClient.builder(HttpHost.create(uris)).build();
    }

    @Bean
    public ElasticsearchClient elasticsearchClient(RestClient restClient, ObjectMapper objectMapper) {
        RestClientTransport transport = new RestClientTransport(
            restClient,
            new JacksonJsonpMapper(objectMapper)
        );
        return new ElasticsearchClient(transport);
    }
}
