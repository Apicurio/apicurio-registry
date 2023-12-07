package io.apicurio.registry.storage.impl.kafkasql.keys;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.apicurio.registry.storage.impl.kafkasql.MessageType;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * When the KSQL artifactStore publishes a message to its Kafka topic, the message key will be a class that
 * implements this interface.
 */
@RegisterForReflection
public interface MessageKey {


    @JsonIgnore
    MessageType getType();

    /**
     * Returns the key that should be used when partitioning the messages.
     */
    @JsonIgnore
    String getPartitionKey();
}
