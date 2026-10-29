package com.wishapp.domain;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;

/**
 * Tracks which JSON keys were sent so PATCH can apply fields by presence.
 */
public class UpdateWishRequestDeserializer extends JsonDeserializer<UpdateWishRequest> {

    @Override
    public UpdateWishRequest deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {
        JsonNode node = parser.getCodec().readTree(parser);
        UpdateWishRequest request = new UpdateWishRequest();

        if (node.has("name")) {
            request.setNamePresent(true);
            request.setName(node.get("name").isNull() ? null : node.get("name").asText());
        }
        if (node.has("comment")) {
            request.setCommentPresent(true);
            request.setComment(node.get("comment").isNull() ? null : node.get("comment").asText());
        }
        if (node.has("tags")) {
            request.setTagsPresent(true);
            if (node.get("tags").isNull()) {
                request.setTags(null);
            } else {
                JsonNode tagsNode = node.get("tags");
                String[] tags = new String[tagsNode.size()];
                for (int i = 0; i < tagsNode.size(); i++) {
                    tags[i] = tagsNode.get(i).asText();
                }
                request.setTags(tags);
            }
        }
        if (node.has("picture")) {
            request.setPicturePresent(true);
            request.setPicture(node.get("picture").isNull() ? null : node.get("picture").asText());
        }

        return request;
    }
}
