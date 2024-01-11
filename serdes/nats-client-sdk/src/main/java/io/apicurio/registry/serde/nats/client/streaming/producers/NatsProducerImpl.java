package io.apicurio.registry.serde.nats.client.streaming.producers;


import io.apicurio.registry.serde.NatsSerializer;
import io.apicurio.registry.serde.config.nats.NatsProducerConfig;
import io.apicurio.registry.serde.generic.Utils;
import io.apicurio.registry.serde.nats.client.exceptions.ApicurioNatsException;
import io.nats.client.Connection;
import io.nats.client.JetStream;

import java.util.Properties;

public class NatsProducerImpl<DATA> implements NatsProducer<DATA> {

    private Connection connection;

    private JetStream jetStream;

    private NatsSerializer<DATA> serializer;

    private String subject;


    public NatsProducerImpl(Connection connection, String subject, Properties config) throws Exception {
        this.connection = connection;
        this.subject = subject;

        serializer = Utils.newConfiguredInstance(config.get(NatsProducerConfig.SERIALIZER_CLASS_CONFIG), NatsSerializer.class, org.apache.kafka.common.utils.Utils.propsToMap(config), null);

        jetStream = connection.jetStream();
    }


    @Override
    public void send(DATA message) throws ApicurioNatsException {
        byte[] data = serializer.serialize(subject, message);
        try {
            jetStream.publish(subject, data);
        } catch (Exception ex) {
            throw new ApicurioNatsException(ex);
        }
    }


    @Override
    public void close() throws Exception {
        //connection.close();
    }
}
