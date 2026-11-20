package io.apicurio.registry.storage.impl.sql;

import io.apicurio.registry.storage.dto.OutboxEvent;
import io.apicurio.common.apps.config.Info;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_STORAGE;

@ApplicationScoped
public class SqlEventsProcessor {

    @ConfigProperty(name = "apicurio.storage.kind", defaultValue = "sql")
    @Info(category = CATEGORY_STORAGE, description = "Application storage variant, for example, sql, kafkasql, gitops, or kubernetesops", availableSince = "3.0.0")
    String storageType;

    @Inject
    SqlRegistryStorage sqlStore;

    public void processEvent(@Observes SqlOutboxEvent event) {
        // Other variants use a SQL storage internally (e.g. GitOps loading its data through imports),
        // but only the SQL variant publishes events through the outbox.
        if ("sql".equals(storageType) && sqlStore.supportsDatabaseEvents() && sqlStore.isReady()) {
            OutboxEvent outboxEvent = event.getOutboxEvent();
            if (outboxEvent != null) {
                sqlStore.createEvent(outboxEvent);
            }
        }
    }
}
