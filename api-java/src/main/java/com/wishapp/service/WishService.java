package com.wishapp.service;

import com.wishapp.domain.Wish;
import com.wishapp.domain.CreateWishRequest;
import com.wishapp.domain.WishDTO;
import com.wishapp.domain.UpdateWishRequest;
import com.wishapp.repository.WishRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service layer for Wish entity operations.
 *
 * <p>Provides CRUD operations with image processing and soft-delete support.</p>
 */
@Service
@Transactional
public class WishService {

    private final WishRepository wishRepository;
    private final ImageProcessingService imageProcessingService;

    public WishService(WishRepository wishRepository, ImageProcessingService imageProcessingService) {
        this.wishRepository = wishRepository;
        this.imageProcessingService = imageProcessingService;
    }

    /**
     * Find all non-deleted wishes.
     *
     * @return list of all non-deleted wishes as DTOs
     */
    public List<WishDTO> findAll() {
        return wishRepository.findAllByDeletedAtIsNullOrderByIdAsc()
            .stream()
            .map(WishDTO::fromEntity)
            .toList();
    }

    /**
     * Find a non-deleted wish by ID.
     *
     * @param id the wish ID
     * @return the wish DTO if found
     */
    public WishDTO findById(Long id) {
        return wishRepository.findByIdAndDeletedAtIsNull(id)
            .map(WishDTO::fromEntity)
            .orElse(null);
    }

    /**
     * Create a new wish.
     *
     * @param request the create request with image base64
     * @return the created wish DTO
     */
    public WishDTO create(CreateWishRequest request) {
        String processedImage = imageProcessingService.resizeAndConvertToWebP(request.picture());

        Wish wish = new Wish();
        wish.setName(request.name());
        wish.setTags(request.tags() != null ? request.tags() : new String[0]);
        wish.setComment(request.comment());
        wish.setPicture(processedImage != null ? java.util.Base64.getDecoder().decode(processedImage) : new byte[0]);

        Wish saved = wishRepository.save(wish);
        return WishDTO.fromEntity(saved);
    }

    /**
     * Update an existing wish.
     *
     * @param id the wish ID
     * @param request the update request with optional image base64
     * @return the updated wish DTO if found
     */
    public WishDTO update(Long id, UpdateWishRequest request) {
        Wish existing = wishRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new RuntimeException("Wish not found: " + id));

        String processedImage = null;
        if (request.getPicture() != null && !request.getPicture().isEmpty()) {
            processedImage = imageProcessingService.resizeAndConvertToWebP(request.getPicture());
        }

        existing.setName(request.getName());
        existing.setTags(request.getTags() != null ? request.getTags() : existing.getTags());
        existing.setComment(request.getComment());
        if (processedImage != null) {
            existing.setPicture(java.util.Base64.getDecoder().decode(processedImage));
        }

        Wish updated = wishRepository.save(existing);
        return WishDTO.fromEntity(updated);
    }

    /**
     * Soft delete a wish.
     *
     * @param id the wish ID
     * @return true if the wish was found and deleted
     */
    public boolean delete(Long id) {
        Wish existing = wishRepository.findByIdAndDeletedAtIsNull(id)
            .orElse(null);
        if (existing == null) {
            return false;
        }

        existing.setDeletedAt(Instant.now());
        wishRepository.save(existing);
        return true;
    }

    /**
     * Find wishes updated after a timestamp (for delta sync).
     *
     * @param timestamp the reference timestamp
     * @return list of updated wishes as DTOs
     */
    public List<WishDTO> findByUpdatedAtAfter(Instant timestamp) {
        return wishRepository.findByUpdatedAtAfter(timestamp)
            .stream()
            .map(WishDTO::fromEntity)
            .toList();
    }

    /**
     * Convert and resize an image to WebP.
     *
     * @param base64Image the base64-encoded image
     * @return base64-encoded WebP image
     */
    public String convertImage(String base64Image) {
        return imageProcessingService.resizeAndConvertToWebP(base64Image);
    }
}
