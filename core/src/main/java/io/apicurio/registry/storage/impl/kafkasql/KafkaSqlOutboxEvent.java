package io.apicurio.registry.storage.impl.kafkasql;

import io.apicurio.registry.storage.dto.OutboxEvent;

public class KafkaSqlOutboxEvent {

    private final OutboxEvent outboxEvent;

    private KafkaSqlOutboxEvent(OutboxEvent outboxEvent) {
        this.outboxEvent = outboxEvent;
    }

    public static KafkaSqlOutboxEvent of(OutboxEvent outboxEvent) {
        return new KafkaSqlOutboxEvent(outboxEvent);
    }

    public OutboxEvent getOutboxEvent() {
        return outboxEvent;
    }
}
