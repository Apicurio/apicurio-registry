package io.apicurio.registry.nats.streaming.producers;


import io.apicurio.registry.nats.ConfigurationProvider;
import io.apicurio.registry.nats.streaming.consumers.ConnectionFactory;
import io.apicurio.registry.nats.exceptions.NatsClientException;
import io.apicurio.registry.serde.SerdeConfig;
import io.apicurio.registry.serde.avro.AvroKafkaSerializer;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.JetStreamApiException;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.Serializer;

public class NatsProducerImpl implements NatsProducer{

    private Connection connection;

    private JetStream jetStream;

    private Serializer serializer;

    private String subject;

    private static Map<String, NatsProducer> cachedClient = new ConcurrentHashMap<String, NatsProducer>();

    private NatsProducerImpl (String subject) throws IOException, InterruptedException {
        this.subject = subject;
        this.serializer = new AvroKafkaSerializer();
        this.serializer.configure(getConfig(), false);
        connection = ConnectionFactory.getConnection();
        jetStream = connection.jetStream();
    }

    public static NatsProducer getInstance(String subject) throws NatsClientException {
        if(cachedClient.containsKey(subject)){
            return cachedClient.get(subject);
        }
        try {
            NatsProducerImpl impl = new NatsProducerImpl(subject);
            cachedClient.putIfAbsent(subject, impl);
        }catch (IOException | InterruptedException e){
            throw new NatsClientException(e);
        }
        return getInstance(subject);
    }

    @Override
    public <T> void sendMessage(T message) throws NatsClientException {
        byte[] data = serializer.serialize(subject, message);
        try {
            jetStream.publish(subject, data);
        } catch (IOException |JetStreamApiException e) {
            throw new NatsClientException(e);
        }
    }

    @Override
    public <T> void sendMessages(Collection<T> messages) throws NatsClientException {

    }

    @Override
    public void closeProducer() throws IOException, InterruptedException {
        connection.close();
    }

    public Map<String, String> getConfig() { //Making it public, for clients to add values
        Map<String, String> config = new HashMap<>();
        config.put(ProducerConfig.CLIENT_ID_CONFIG, "Producer-" + subject);
        config.put(SerdeConfig.ARTIFACT_RESOLVER_STRATEGY, ConfigurationProvider.getString(SerdeConfig.ARTIFACT_RESOLVER_STRATEGY));
        config.put(SerdeConfig.REGISTRY_URL, ConfigurationProvider.getString(SerdeConfig.REGISTRY_URL));
        config.putIfAbsent(SerdeConfig.AUTO_REGISTER_ARTIFACT, ConfigurationProvider.getString(SerdeConfig.AUTO_REGISTER_ARTIFACT));
        config.put(SerdeConfig.USE_ID, ConfigurationProvider.getString(SerdeConfig.USE_ID));
        return config;
    }
}
