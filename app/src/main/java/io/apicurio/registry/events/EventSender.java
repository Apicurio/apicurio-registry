package io.apicurio.registry.events;

import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.OutboxEvent;
import io.apicurio.registry.types.Current;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

@ApplicationScoped
public class EventSender {

    @Inject
    @Current
    RegistryStorage storage;

    public void onExportedEvent(@Observes OutboxEvent event) {
        String createdEvent = storage.createEvent(event);
    }
}
