package io.apicurio.registry.storage.impl.sql.repositories;

import io.apicurio.registry.storage.dto.OutboxEvent;
import io.apicurio.registry.storage.impl.sql.HandleFactory;
import io.apicurio.registry.storage.impl.sql.SqlStatements;
import org.slf4j.Logger;

/**
 * Repository handling event/outbox operations in the SQL storage layer.
 * Extracted from AbstractSqlRegistryStorage to improve maintainability.
 */
public class SqlEventRepository {

    private final Logger log;

    private final SqlStatements sqlStatements;

    private final HandleFactory handles;

    private final String eventsTopic;

    public SqlEventRepository(HandleFactory handles, SqlStatements sqlStatements, Logger log,
            String eventsTopic) {
        this.handles = handles;
        this.sqlStatements = sqlStatements;
        this.log = log;
        this.eventsTopic = eventsTopic;
    }

    /**
     * Create an outbox event for database-driven event publishing.
     */
    public String createEvent(OutboxEvent event) {
        if(event == null)
        {
            log.warn("Cannot create outbox event: event is null");
            return null;
        }
        if (supportsDatabaseEvents()) {
            handles.withHandle(handle -> {
                handle.createUpdate(sqlStatements.createOutboxEvent()).bind(0, event.getId())
                        .bind(1, eventsTopic).bind(2, event.getAggregateId()).bind(3, event.getType())
                        .bind(4, (event.getPayload() != null ? event.getPayload().toString() : null)).execute();

                return handle.createUpdate(sqlStatements.deleteOutboxEvent()).bind(0, event.getId())
                        .execute();
            });
            log.trace("Created outbox event {} of type {} for aggregate {}", event.getId(), event.getType(),
                    event.getAggregateId());
        } else {
            log.debug(
                    "Database-driven events are not supported for db type '{}'; event {} of type {} was not persisted to the outbox",
                    sqlStatements.dbType(), event.getId(), event.getType());
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
        return "postgresql".equals(sqlStatements.dbType());
    }

    private boolean isMssql() {
        return "mssql".equals(sqlStatements.dbType());
    }
}
