package com.wishapp.controller;

import com.wishapp.domain.CreateWishRequest;
import com.wishapp.domain.WishDTO;
import com.wishapp.domain.UpdateWishRequest;
import com.wishapp.service.WishService;
import jakarta.validation.Valid;
import java.lang.management.ManagementFactory;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Wish CRUD, image conversion, and health/status on the Java API.
 *
 * <p>Browse and search live on {@link WishSearchController} ({@code GET /wishes}).</p>
 */
@RestController
@RequestMapping("")
public class WishController {

    private final WishService wishService;

    public WishController(WishService wishService) {
        this.wishService = wishService;
    }

    /**
     * GET /status - Returns status information.
     *
     * @return status response with timestamp and uptime
     */
    @GetMapping("/status")
    public ResponseEntity<StatusResponse> getStatus() {
        return ResponseEntity.ok(new StatusResponse(
            "ok",
            Instant.now().toString(),
            ManagementFactory.getRuntimeMXBean().getUptime() / 1000.0
        ));
    }

    /**
     * POST /new-wish - Create a new wish.
     *
     * @param request the create request
     * @return 204 No Content
     */
    @PostMapping("/new-wish")
    public ResponseEntity<Void> createWish(@Valid @RequestBody CreateWishRequest request) {
        wishService.create(request);
        return ResponseEntity.noContent().build();
    }

    /**
     * PATCH /update-wish - Update an existing wish.
     *
     * @param request the update request with id
     * @return updated wish DTO if found, 404 if not found
     */
    @PatchMapping("/update-wish")
    public ResponseEntity<WishDTO> updateWish(@Valid @RequestBody UpdateWishRequest request) {
        if (request.getId() == null) {
            return ResponseEntity.badRequest().build();
        }
        try {
            return ResponseEntity.ok(wishService.update(request.getId(), request));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * DELETE /delete-wish/{id} - Soft delete a wish.
     *
     * @param id the wish ID
     * @return 204 No Content if deleted, 400 if invalid ID
     */
    @DeleteMapping("/delete-wish/{id}")
    public ResponseEntity<Void> deleteWish(@PathVariable Long id) {
        if (!wishService.delete(id)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /convert-image - Convert and resize an image.
     *
     * <p>Matches the Express endpoint which responds with {@code res.json(base64String)},
     * i.e. a bare JSON string carrying the base64-encoded WebP image.</p>
     *
     * @param request the convert request with image_base64
     * @return converted image as a base64 WebP JSON string
     */
    @PostMapping("/convert-image")
    public ResponseEntity<String> convertImage(@Valid @RequestBody ConvertImageRequest request) {
        String converted = wishService.convertImage(request.image_base64());
        return ResponseEntity.ok(converted);
    }

    /**
     * Status response DTO.
     */
    public record StatusResponse(String status, String timestamp, double uptime) {
    }

    /**
     * Convert image request DTO.
     */
    public record ConvertImageRequest(String image_base64) {
    }
}
