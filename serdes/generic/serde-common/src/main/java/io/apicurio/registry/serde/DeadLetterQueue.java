package io.apicurio.registry.serde;

import io.apicurio.registry.contracts.rules.RuleViolation;

import java.util.List;

/**
 * Receives the records that fail a data contract rule with {@code onFailure: DLQ}.
 */
public interface DeadLetterQueue {

    /**
     * The topic the records are sent to.
     */
    String topic();

    /**
     * Sends a record to the dead letter queue.
     *
     * @param sourceTopic the topic the record was written to or read from
     * @param payload the serialized record
     * @param violations the failed rules
     */
    void send(String sourceTopic, byte[] payload, List<RuleViolation> violations);
}
