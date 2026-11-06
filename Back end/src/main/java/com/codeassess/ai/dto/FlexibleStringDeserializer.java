package com.codeassess.ai.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Robust Jackson deserializer for String fields that may be returned by LLMs
 * as a String, a JSON Array (e.g. constraints: ["1 <= N <= 10^5", "..."]),
 * a primitive (number/boolean), or an object.
 * Converts arrays to newline-separated strings and preserves all textual content.
 */
public class FlexibleStringDeserializer extends JsonDeserializer<String> {

    @Override
    public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonToken token = p.currentToken();
        if (token == null || token == JsonToken.VALUE_NULL) {
            return null;
        }
        if (token == JsonToken.VALUE_STRING) {
            return p.getText();
        }
        if (token == JsonToken.START_ARRAY) {
            if (p.getCodec() != null) {
                JsonNode arrayNode = p.getCodec().readTree(p);
                List<String> items = new ArrayList<>();
                for (JsonNode item : arrayNode) {
                    if (item == null || item.isNull()) {
                        continue;
                    }
                    if (item.isTextual() || item.isValueNode()) {
                        items.add(item.asText());
                    } else {
                        items.add(item.toString());
                    }
                }
                return String.join("\n", items);
            } else {
                List<String> items = new ArrayList<>();
                while (p.nextToken() != JsonToken.END_ARRAY) {
                    items.add(p.getText());
                }
                return String.join("\n", items);
            }
        }
        if (token == JsonToken.START_OBJECT) {
            if (p.getCodec() != null) {
                JsonNode objectNode = p.getCodec().readTree(p);
                return objectNode.toString();
            } else {
                p.skipChildren();
                return "";
            }
        }
        // Numbers, booleans, etc.
        return p.getText();
    }
}

