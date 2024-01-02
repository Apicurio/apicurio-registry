package io.apicurio.registry.events;

import java.util.Optional;

import io.apicurio.registry.events.dto.RegistryEventType;

public interface EventsService {

    boolean isReady();

    boolean isConfigured();

    void triggerEvent(RegistryEventType type, Optional<String> artifactId, Object data);

}
