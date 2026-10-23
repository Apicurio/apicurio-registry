package io.apicurio.registry.events.dto;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class WebhookEventTypes {

    /** Supported types, in declaration order. */
    public static final Set<CloudEventType> SUPPORTED = Collections.unmodifiableSet(EnumSet.of(
            CloudEventType.ARTIFACT_CREATED,
            CloudEventType.ARTIFACT_UPDATED,
            CloudEventType.ARTIFACT_DELETED,
            CloudEventType.ARTIFACT_VERSION_PUBLISHED,
            CloudEventType.ARTIFACT_VERSION_STATE_CHANGED));

    /** Every {@link CloudEventType} that is not supported. */
    public static final Set<CloudEventType> RESERVED = Collections
            .unmodifiableSet(EnumSet.complementOf(EnumSet.copyOf(SUPPORTED)));

    /**
     * The wire values of {@link #SUPPORTED}, in the same order. Must equal the
     * OpenAPI enum.
     */
    public static final List<String> SUPPORTED_VALUES = SUPPORTED.stream().map(CloudEventType::type).toList();

    private WebhookEventTypes() {
    }

    public static Optional<CloudEventType> fromSupportedValue(String value) {
        return find(SUPPORTED, value);
    }

    public static boolean isReserved(String value) {
        return find(RESERVED, value).isPresent();
    }

    private static Optional<CloudEventType> find(Set<CloudEventType> types, String value) {
        if (value == null) {
            return Optional.empty();
        }
        return types.stream().filter(t -> t.type().equals(value)).findFirst();
    }
}
