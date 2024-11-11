package io.apicurio.registry.storage.impl.sql;

import io.apicurio.registry.storage.dto.OutboxEvent;

public class SqlOutboxEvent {

    private final OutboxEvent outboxEvent;

    private SqlOutboxEvent(OutboxEvent outboxEvent) {
        this.outboxEvent = outboxEvent;
    }

    public static SqlOutboxEvent of(OutboxEvent outboxEvent) {
        return new SqlOutboxEvent(outboxEvent);
    }

    public OutboxEvent getOutboxEvent() {
        return outboxEvent;
    }
}
