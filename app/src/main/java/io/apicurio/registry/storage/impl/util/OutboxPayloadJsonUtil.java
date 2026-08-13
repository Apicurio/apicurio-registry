package io.apicurio.registry.storage.impl.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.apicurio.registry.util.JsonObjectMapper;

public final class OutboxPayloadJsonUtil {

    private OutboxPayloadJsonUtil() {
    }

    public static String toJsonString(Object payload) {
        try {
            return JsonObjectMapper.MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox event payload", e);
        }
    }
}