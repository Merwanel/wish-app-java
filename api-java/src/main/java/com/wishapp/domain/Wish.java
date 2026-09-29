package com.wishapp.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Domain entity representing a wish item.
 *
 * <p>Maps to the 'wishes' database table with soft-deletion support via deleted_at timestamp.</p>
 */
@Entity
@Table(name = "wishes")
@Getter
@Setter
@NoArgsConstructor
public class Wish {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @NotBlank
    @Size(max = 255)
    private String name;

    @Column(name = "tags", columnDefinition = "TEXT[]")
    private String[] tags = new String[0];

    @NotBlank
    private String comment;

    @Lob
    @Column(name = "picture", nullable = false)
    private byte[] picture;

    @PrePersist
    protected void onCreate() {
        final Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Wish wish = (Wish) o;
        return id != null && id.equals(wish.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "Wish{" +
            "id=" + id +
            ", name='" + name + '\'' +
            ", tags=" + Arrays.toString(tags) +
            ", comment='" + comment + '\'' +
            ", pictureLength=" + (picture != null ? picture.length : 0) +
            '}';
    }
}
