package io.apicurio.registry.kafka;

import io.apicurio.registry.kafka.proto.Reg;
import org.apache.kafka.clients.consumer.ConsumerRecord;

/**
 * @author Ales Justin
 */
public interface KafkaRegistryStorageHandle {
    void consumeSchemaValue(ConsumerRecord<Reg.UUID, Reg.SchemaValue> record);
}
