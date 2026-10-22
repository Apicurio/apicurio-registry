package io.apicurio.registry.storage.impl.kafkasql;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.apache.kafka.common.Uuid;

/**
 * When the KSQL artifactStore publishes a message to its Kafka topic, the message key will be a class that
 * implements this interface.
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@EqualsAndHashCode(callSuper = false)
@ToString
public class KafkaSqlMessageKey {

    @Builder.Default
    private String uuid = Uuid.randomUuid().toString();
    private String messageType;
    @Builder.Default
    private String partitionKey = "__GLOBAL_PARTITION__";

}
