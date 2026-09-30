package com.wishapp.controller;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * REST controller for search-related operations.
 *
 * <p>Proxies SSE stream from image-scraper service and caches results in Redis.</p>
 */
@RestController
@RequestMapping("")
public class SearchController {

    private final SseEmitterManager sseEmitterManager;

    public SearchController(SseEmitterManager sseEmitterManager) {
        this.sseEmitterManager = sseEmitterManager;
    }

    /**
     * GET /search/{searchTerm} - Proxy SSE stream from image-scraper.
     *
     * @param searchTerm the search term
     * @return SSE emitter for streaming images
     */
    @GetMapping(value = "/search/{searchTerm}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter search(@PathVariable String searchTerm) {
        return sseEmitterManager.createEmitter(searchTerm);
    }

    /**
     * Manager for SSE emitters and caching.
     */
    @Component
    public static class SseEmitterManager {

        private final String imageScraperUrl;
        private final RedisTemplate<String, String> redisTemplate;
        private final ObjectMapper objectMapper;
        
        // Cache TTL in seconds (1 hour)
        private static final long CACHE_TTL_SECONDS = 3600;
        private static final String CACHE_KEY_PREFIX = "search:results:";

        public SseEmitterManager(
                @Value("${image.scraper.url:http://localhost:3001}") String imageScraperUrl,
                RedisTemplate<String, String> redisTemplate,
                ObjectMapper objectMapper) {
            this.imageScraperUrl = imageScraperUrl;
            this.redisTemplate = redisTemplate;
            this.objectMapper = objectMapper;
        }

        public SseEmitter createEmitter(String searchTerm) {
            SseEmitter emitter = new SseEmitter(60000L); // 60 second timeout

            // Start proxying from image-scraper with caching
            startProxyWithCaching(searchTerm, emitter);

            emitter.onCompletion(() -> {
                // Cleanup if needed
            });
            emitter.onTimeout(() -> {
                emitter.complete();
            });
            emitter.onError(throwable -> {
                emitter.completeWithError(throwable);
            });

            return emitter;
        }

        private void startProxyWithCaching(String searchTerm, SseEmitter emitter) {
            new Thread(() -> {
                String cacheKey = CACHE_KEY_PREFIX + searchTerm.toLowerCase();
                
                // Try to get from cache first
                List<String> cachedResults = getFromCache(cacheKey);
                if (cachedResults != null && !cachedResults.isEmpty()) {
                    // Send cached results
                    sendCachedResults(cachedResults, emitter);
                    return;
                }
                
                // If not in cache, fetch from image-scraper and cache results
                fetchAndCache(searchTerm, cacheKey, emitter);
            }).start();
        }

        private List<String> getFromCache(String cacheKey) {
            try {
                String cachedData = redisTemplate.opsForValue().get(cacheKey);
                if (cachedData != null && !cachedData.isEmpty()) {
                    return objectMapper.readValue(cachedData, 
                        objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
                }
            } catch (JsonProcessingException e) {
                // If cache data is corrupted, we'll just fetch fresh data
            }
            return null;
        }

        private void sendCachedResults(List<String> cachedResults, SseEmitter emitter) {
            try {
                for (String result : cachedResults) {
                    emitter.send("data: " + result);
                }
                emitter.send("data: {\"type\": \"complete\", \"cached\": true}");
                emitter.complete();
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
        }

        private void fetchAndCache(String searchTerm, String cacheKey, SseEmitter emitter) {
            HttpURLConnection connection = null;
            BufferedReader reader = null;
            List<String> results = new ArrayList<>();
            
            try {
                URL url = new URL(imageScraperUrl + "/internal/scrape/" + searchTerm);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(55000);

                if (connection.getResponseCode() != 200) {
                    emitter.completeWithError(new Exception("Failed to connect to image-scraper: " + connection.getResponseCode()));
                    return;
                }

                reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data:")) {
                        String data = line.substring(5).trim();
                        emitter.send(data);
                        results.add(data);
                    } else if (line.isEmpty()) {
                        // End of message block
                    }
                }

                // Cache the results
                cacheResults(cacheKey, results);
                
                emitter.send("data: {\"type\": \"complete\", \"cached\": false}");
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException ignored) {
                    }
                }
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }

        private void cacheResults(String cacheKey, List<String> results) {
            try {
                String resultsJson = objectMapper.writeValueAsString(results);
                redisTemplate.opsForValue().set(cacheKey, resultsJson, CACHE_TTL_SECONDS, TimeUnit.SECONDS);
            } catch (JsonProcessingException e) {
                // Log error but don't fail the request
                System.err.println("Failed to cache search results: " + e.getMessage());
            }
        }
    }
}
