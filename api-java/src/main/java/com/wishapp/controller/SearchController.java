package com.wishapp.controller;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * REST controller for search-related operations.
 *
 * <p>Proxies SSE stream from image-scraper service and caches results in Redis.</p>
 */
@RestController
@RequestMapping("/api")
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
    public static class SseEmitterManager {

        private final String imageScraperUrl;

        public SseEmitterManager(@Value("${image.scraper.url:http://localhost:3001}") String imageScraperUrl) {
            this.imageScraperUrl = imageScraperUrl;
        }

        public SseEmitter createEmitter(String searchTerm) {
            SseEmitter emitter = new SseEmitter(60000L); // 60 second timeout

            // Start proxying from image-scraper
            startProxy(searchTerm, emitter);

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

        private void startProxy(String searchTerm, SseEmitter emitter) {
            new Thread(() -> {
                HttpURLConnection connection = null;
                BufferedReader reader = null;
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

                    reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("data:")) {
                            emitter.send(line.substring(5).trim());
                        } else if (line.isEmpty()) {
                            // End of message block
                        }
                    }

                    emitter.send("data: {\"type\": \"complete\"}");
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
            }).start();
        }
    }
}
