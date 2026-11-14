package io.apicurio.registry.storage.impl.sql;

import io.apicurio.registry.events.ContractRulesetConfigured;
import io.apicurio.registry.storage.dto.OutboxEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Test-only observer of {@link SqlOutboxEvent}, used by {@link AbstractContractOutboxTransactionTest} to inspect
 * the contract event payloads the SQL storage emits.
 * <p>
 * The bean is active in every Quarkus test, so it only records events for aggregates belonging to that
 * test: artifact-scoped events whose group starts with {@link #GROUP_PREFIX}, plus global contract
 * ruleset events, which carry a fixed aggregate id and therefore cannot be namespaced per test.
 * </p>
 */
@ApplicationScoped
public class ContractOutboxEventRecorder {

    static final String GROUP_PREFIX = "contract-outbox-tx-";

    private static final String GLOBAL_AGGREGATE_ID = ContractRulesetConfigured.GLOBAL_COORDINATE + "-"
            + ContractRulesetConfigured.GLOBAL_COORDINATE;

    private final Queue<OutboxEvent> events = new ConcurrentLinkedQueue<>();

    public void onOutboxEvent(@Observes SqlOutboxEvent event) {
        OutboxEvent outboxEvent = event.getOutboxEvent();
        if (outboxEvent == null) {
            return;
        }
        String aggregateId = outboxEvent.getAggregateId();
        if (aggregateId == null) {
            return;
        }
        if (aggregateId.startsWith(GROUP_PREFIX) || GLOBAL_AGGREGATE_ID.equals(aggregateId)) {
            events.add(outboxEvent);
        }
    }

    void clear() {
        events.clear();
    }

    /**
     * Returns the recorded events of the given {@link OutboxEvent#getType()}, in emission order.
     */
    List<OutboxEvent> eventsOfType(String type) {
        return events.stream().filter(event -> type.equals(event.getType())).toList();
    }
}
