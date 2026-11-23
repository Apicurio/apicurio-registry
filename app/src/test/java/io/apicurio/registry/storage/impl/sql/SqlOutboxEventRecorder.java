package io.apicurio.registry.storage.impl.sql;

import io.apicurio.registry.storage.dto.OutboxEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Records the outbox events fired by SQL storage, in the order they are written to the outbox table,
 * and can simulate a failure to write one.
 */
@ApplicationScoped
public class SqlOutboxEventRecorder {

    private final List<OutboxEvent> events = new CopyOnWriteArrayList<>();

    private volatile String failOnType;

    void record(@Observes SqlOutboxEvent event) {
        if (event.getOutboxEvent() != null) {
            if (event.getOutboxEvent().getType().equals(failOnType)) {
                throw new IllegalStateException("Simulated outbox failure");
            }
            events.add(event.getOutboxEvent());
        }
    }

    /**
     * Makes writing an event of the given type fail, as a failing outbox insert would.
     * Pass null to stop failing.
     */
    public void failOn(String eventType) {
        this.failOnType = eventType;
    }

    /** @return the types of the recorded events whose payload has the given groupId, in firing order */
    public List<String> typesForGroup(String groupId) {
        return events.stream().filter(e -> groupId.equals(e.getPayload().optString("groupId", null)))
                .map(OutboxEvent::getType).toList();
    }
}
