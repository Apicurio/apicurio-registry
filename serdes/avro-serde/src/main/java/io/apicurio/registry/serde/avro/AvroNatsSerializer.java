package io.apicurio.registry.serde.avro;

import io.apicurio.registry.serde.NatsSerializer;
import io.apicurio.registry.serde.config.nats.NatsProducerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;

import java.util.Map;

public class AvroNatsSerializer<DATA> implements NatsSerializer<DATA> {


    private final AvroKafkaSerializer<DATA> kafkaSerializer;


    public AvroNatsSerializer() {
        kafkaSerializer = new AvroKafkaSerializer<>();
    }


    @Override
    public void configure(Map<String, Object> configs) {
        //configs.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, configs.get(NatsProducerConfig.SERIALIZER_CLASS_CONFIG));
        configs.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, configs.get(NatsProducerConfig.SERIALIZER_CLASS_CONFIG));
        kafkaSerializer.configure(configs, false);
    }


    @Override
    public byte[] serialize(String subject, DATA data) {
        return kafkaSerializer.serialize(subject, data);
    }
}
