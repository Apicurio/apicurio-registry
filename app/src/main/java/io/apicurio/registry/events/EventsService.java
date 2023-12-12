package io.apicurio.registry.events;

import io.apicurio.registry.events.dto.RegistryEventType;

import java.util.Optional;

public interface EventsService {

    boolean isReady();

    boolean isConfigured();

    void triggerEvent(RegistryEventType type, Optional<String> artifactId, Object data);

}
