package io.apicurio.registry.storage.impl.sql.repositories;

import io.apicurio.registry.storage.dto.SchemaUsageEventDto;
import io.apicurio.registry.storage.impl.sql.HandleFactory;
import io.apicurio.registry.storage.impl.sql.SqlStatements;
import org.slf4j.Logger;

import java.util.List;

public class SqlUsageRepository {

    private final Logger log;
    private final SqlStatements sqlStatements;
    private final HandleFactory handles;

    public SqlUsageRepository(HandleFactory handles, SqlStatements sqlStatements, Logger log) {
        this.handles = handles;
        this.sqlStatements = sqlStatements;
        this.log = log;
    }

    public void recordUsageEvents(List<SchemaUsageEventDto> events) {
        handles.withHandle(handle -> {
            var batch = handle.createUpdate(sqlStatements.insertSchemaUsage());
            for (SchemaUsageEventDto event : events) {
                batch.bind(0, event.getGlobalId())
                        .bind(1, event.getClientId())
                        .bind(2, event.getOperation())
                        .bind(3, event.getEventTimestamp())
                        .execute();
            }
            return null;
        });
    }
}
