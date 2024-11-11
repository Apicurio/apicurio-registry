package io.apicurio.registry.storage.impl.sql;

import io.apicurio.registry.storage.dto.OutboxEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

@ApplicationScoped
public class SqlEventsProcessor {

    @Inject
    SqlRegistryStorage sqlStore;

    public void processEvent(@Observes SqlOutboxEvent event) {
        if (sqlStore.supportsDatabaseEvents() && sqlStore.isReady()) {
            OutboxEvent outboxEvent = event.getOutboxEvent();
            sqlStore.createEvent(outboxEvent);
        }
    }
}
