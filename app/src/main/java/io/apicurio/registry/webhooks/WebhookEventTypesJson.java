package io.apicurio.registry.webhooks;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import io.apicurio.registry.util.JsonObjectMapper;

import java.util.List;

/**
 * Converts a subscription's event types to and from the JSON array stored in the
 * {@code webhook_subscriptions.eventTypes} column. An empty list is stored as {@code "[]"}, meaning every
 * supported type; the column is never {@code NULL}.
 */
public final class WebhookEventTypesJson {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private WebhookEventTypesJson() {
    }

    public static String toJson(List<String> eventTypes) {
        try {
            return JsonObjectMapper.MAPPER.writeValueAsString(eventTypes == null ? List.of() : eventTypes);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize webhook event types.", e);
        }
    }

    public static List<String> fromJson(String json) {
        try {
            return JsonObjectMapper.MAPPER.readValue(json, STRING_LIST);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored webhook event types are not a JSON array of strings.", e);
        }
    }
}
