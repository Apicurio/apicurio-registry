package io.apicurio.registry.storage.impl.sql.repositories;

import io.apicurio.registry.storage.dto.OutboxEvent;
import io.apicurio.registry.storage.impl.sql.HandleFactory;
import io.apicurio.registry.storage.impl.sql.SqlStatements;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;

/**
 * Repository handling event/outbox operations in the SQL storage layer.
 * Extracted from AbstractSqlRegistryStorage to improve maintainability.
 */
@ApplicationScoped
public class SqlEventRepository {

    @Inject
    Logger log;

    @Inject
    SqlStatements sqlStatements;

    @Inject
    HandleFactory handles;

    /**
     * Set the HandleFactory to use for database operations.
     * This allows storage implementations to override the default injected HandleFactory.
     */
    public void setHandleFactory(HandleFactory handleFactory) {
        this.handles = handleFactory;
    }

    @Inject
    @ConfigProperty(name = "apicurio.events.kafka.topic", defaultValue = "registry-events")
    String eventsTopic;

    /**
     * Create an outbox event for database-driven event publishing.
     */
    public String createEvent(OutboxEvent event) {
        if (supportsDatabaseEvents()) {
            handles.withHandle(handle -> {
                handle.createUpdate(sqlStatements.createOutboxEvent())
                        .bind(0, event.getId())
                        .bind(1, eventsTopic)
                        .bind(2, event.getAggregateId())
                        .bind(3, event.getType())
                        .bind(4, event.getPayload().toString())
                        .execute();

                return handle.createUpdate(sqlStatements.deleteOutboxEvent())
                        .bind(0, event.getId())
                        .execute();
            });
        }
        return event.getId();
    }

    /**
     * Check if the database supports database-driven events.
     */
    public boolean supportsDatabaseEvents() {
        return isPostgresql() || isMssql();
    }

    private boolean isPostgresql() {
        return sqlStatements.dbType().equals("postgresql");
    }

    private boolean isMssql() {
        return sqlStatements.dbType().equals("mssql");
    }
}
