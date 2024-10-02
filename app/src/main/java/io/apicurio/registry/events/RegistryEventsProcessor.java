package io.apicurio.registry.events;

import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.OutboxEvent;
import io.apicurio.registry.types.Current;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

@ApplicationScoped
public class RegistryEventsProcessor {

    @Inject
    @Current
    RegistryStorage storage;

    public void processEvent(@Observes OutboxEvent event) {
        storage.createEvent(event);
    }
}
