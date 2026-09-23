package io.apicurio.registry.storage.impl.sql.repositories;

import io.apicurio.registry.storage.dto.OutboxEvent;
import io.apicurio.registry.storage.impl.sql.HandleFactory;
import io.apicurio.registry.storage.impl.sql.SqlStatements;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SqlEventRepositoryTest {
    @Test
    public void testCreateNullEvent() {
        SqlEventRepository repository = new SqlEventRepository(
                Mockito.mock(HandleFactory.class),
                Mockito.mock(SqlStatements.class),
                Mockito.mock(Logger.class),
                "events-topic"
        );
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> repository.createEvent(null));
        assertEquals("OutboxEvent must not be null", exception.getMessage());
    }

    @Test
    public void testCreateEventWithNullPayload() {
        SqlEventRepository repository = new SqlEventRepository(
                Mockito.mock(HandleFactory.class),
                Mockito.mock(SqlStatements.class),
                Mockito.mock(Logger.class),
                "events-topic"
        );

        OutboxEvent eventWithNullPayload = new OutboxEvent("id-123", "agg-123") {
            @Override public JSONObject getPayload() {
                return null;
            }

            @Override public String getType() {
                return "TEST_TYPE";
            }
        };

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> repository.createEvent(eventWithNullPayload));
        assertEquals("OutboxEvent payload must not be null", exception.getMessage());
    }
}
