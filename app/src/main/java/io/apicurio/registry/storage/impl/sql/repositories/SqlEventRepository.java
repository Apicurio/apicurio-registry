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

    public SqlEventRepository(HandleFactory handles, SqlStatements sqlStatements, Logger log, String eventsTopic) {
        this.handles = handles;
        this.sqlStatements = sqlStatements;
        this.log = log;
        this.eventsTopic = eventsTopic;
    }

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
