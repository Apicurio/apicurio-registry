package io.apicurio.registry.serde.avro.nats.client.streaming.producers;

import io.apicurio.registry.serde.avro.AvroSerdeConfig;
import io.apicurio.registry.serde.avro.AvroSerializer;
import io.apicurio.registry.serde.avro.nats.client.exceptions.ApicurioNatsException;
import io.nats.client.Connection;
import io.nats.client.JetStream;

import java.io.IOException;
import java.util.Map;

public class NatsProducerImpl<DATA> implements NatsProducer<DATA> {

    private final Connection connection;

    private final JetStream jetStream;

    private final AvroSerializer<DATA> serializer;

    private final String subject;

    public NatsProducerImpl(Connection connection, String subject, Map<String, Object> config)
            throws IOException {
        this.connection = connection;
        this.subject = subject;
        AvroSerdeConfig deserializerConfig = new AvroSerdeConfig(config);
        serializer = new AvroSerializer<>();
        serializer.configure(deserializerConfig, false);
        // config.get(NatsProducerConfig.SERIALIZER_CLASS_CONFIG)

        jetStream = connection.jetStream();
    }

    @Override
    public void send(DATA message) throws ApicurioNatsException {
        byte[] data = serializer.serializeData(subject, message);

        try {
            jetStream.publish(subject, data);
        } catch (Exception ex) {
            throw new ApicurioNatsException(ex);
        }
    }

    @Override
    public void close() throws Exception {
        connection.close();
    }
}
