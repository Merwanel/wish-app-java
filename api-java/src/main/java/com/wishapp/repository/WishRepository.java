package com.wishapp.repository;

import com.wishapp.domain.Wish;
import java.time.Instant;
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
     * Find wishes updated after a given timestamp (for delta sync).
     *
     * @param timestamp the reference timestamp
     * @return list of wishes updated after the timestamp
     */
    @Query("SELECT w FROM Wish w WHERE w.updatedAt > :timestamp AND w.deletedAt IS NULL")
    List<Wish> findByUpdatedAtAfter(@Param("timestamp") Instant timestamp);
}
