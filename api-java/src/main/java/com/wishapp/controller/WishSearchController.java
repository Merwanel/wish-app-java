package com.wishapp.controller;

import com.wishapp.domain.WishSearchResponse;
import com.wishapp.service.WishEtlService;
import com.wishapp.service.WishSearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Wish search (Elasticsearch) and internal ETL trigger endpoints.
 */
@RestController
@RequestMapping("")
public class WishSearchController {

    private final WishSearchService wishSearchService;
    private final WishEtlService wishEtlService;

    public WishSearchController(WishSearchService wishSearchService, WishEtlService wishEtlService) {
        this.wishSearchService = wishSearchService;
        this.wishEtlService = wishEtlService;
    }

    /**
     * GET /wishes/search — fuzzy search / browse with tag facets.
     *
     * <p>Empty or missing {@code q} uses {@code match_all} sorted by {@code createdAt} desc.</p>
     */
    @GetMapping("/wishes/search")
    public ResponseEntity<WishSearchResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return ResponseEntity.ok(wishSearchService.search(q, tag, limit, offset));
    }

    /**
     * POST /internal/etl/sync — on-demand delta sync with forced refresh (manual/tests).
     */
    @PostMapping("/internal/etl/sync")
    public ResponseEntity<MapBody> syncEtl() {
        int processed = wishEtlService.sync(true);
        return ResponseEntity.ok(new MapBody(processed));
    }

    public record MapBody(int processed) {}
}
