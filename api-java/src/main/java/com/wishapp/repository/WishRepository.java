package com.wishapp.repository;

import com.wishapp.domain.Wish;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JPA repository for Wish entity.
 *
 * <p>Provides soft-delete aware queries via deleted_at column.</p>
 */
public interface WishRepository extends JpaRepository<Wish, Long> {

    /**
     * Find all non-deleted wishes ordered by ID ascending.
     *
     * @return list of non-deleted wishes
     */
    @Query("SELECT w FROM Wish w WHERE w.deletedAt IS NULL ORDER BY w.id ASC")
    List<Wish> findAllByDeletedAtIsNullOrderByIdAsc();

    /**
     * Find a non-deleted wish by ID.
     *
     * @param id the wish ID
     * @return optional wish if found and not deleted
     */
    @Query("SELECT w FROM Wish w WHERE w.id = :id AND w.deletedAt IS NULL")
    Optional<Wish> findByIdAndDeletedAtIsNull(@Param("id") Long id);

    /**
     * Find non-deleted wishes by IDs (for search hydration).
     *
     * @param ids wish IDs from Elasticsearch hits
     * @return matching non-deleted wishes (order not preserved)
     */
    @Query("SELECT w FROM Wish w WHERE w.id IN :ids AND w.deletedAt IS NULL")
    List<Wish> findByIdInAndDeletedAtIsNull(@Param("ids") Collection<Long> ids);

    /**
     * Delta ETL query: all wishes with {@code updatedAt >= timestamp},
     * including soft-deleted rows so ES ghosts can be removed.
     *
     * @param timestamp Redis bookmark ({@code etl:last_sync})
     * @return wishes updated at or after the bookmark
     */
    @Query("SELECT w FROM Wish w WHERE w.updatedAt >= :timestamp ORDER BY w.updatedAt ASC")
    List<Wish> findByUpdatedAtGreaterThanEqual(@Param("timestamp") Instant timestamp);

    /**
     * @deprecated Prefer {@link #findByUpdatedAtGreaterThanEqual(Instant)} which includes soft-deletes.
     */
    @Deprecated
    @Query("SELECT w FROM Wish w WHERE w.updatedAt >= :timestamp ORDER BY w.updatedAt ASC")
    List<Wish> findByUpdatedAtAfter(@Param("timestamp") Instant timestamp);
}
