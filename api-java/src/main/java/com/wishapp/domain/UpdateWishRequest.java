package com.wishapp.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;

/**
 * Partial update body for {@code PATCH /wishes/{id}}.
 *
 * <p>Only keys present in the JSON are applied; {@code null} does not clear {@code tags}.</p>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(using = UpdateWishRequestDeserializer.class)
public class UpdateWishRequest {
    private boolean namePresent;
    private boolean commentPresent;
    private boolean tagsPresent;
    private boolean picturePresent;

    private String name;
    private String[] tags;
    private String comment;
    private String picture;
}
