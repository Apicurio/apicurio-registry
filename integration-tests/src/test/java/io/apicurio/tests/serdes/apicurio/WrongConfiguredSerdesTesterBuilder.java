package io.apicurio.tests.serdes.apicurio;


import io.apicurio.tests.serdes.apicurio.SerdesTester.DataGenerator;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

import static io.apicurio.tests.serdes.apicurio.Tester.*;
import static org.junit.jupiter.api.Assertions.assertThrows;


public class WrongConfiguredSerdesTesterBuilder<P> implements TesterBuilder {

    private DataGenerator<P> dataGenerator;

    private String topic;

    private Class<?> artifactResolverStrategy;

    private Class<?> serializer;

    private Properties producerProperties = new Properties();

    public WrongConfiguredSerdesTesterBuilder() {
        super();
    }

    public WrongConfiguredSerdesTesterBuilder<P> withProducerProperty(String key, String value) {
        producerProperties.put(key, value);
        return this;
    }

    public <U extends Serializer<?>> WrongConfiguredSerdesTesterBuilder<P> withSerializer(Class<U> serializer) {
        this.serializer = serializer;
        return this;
    }

    public WrongConfiguredSerdesTesterBuilder<P> withTopic(String topic) {
        this.topic = topic;
        return this;
    }

    public WrongConfiguredSerdesTesterBuilder<P> withStrategy(Class<?> strategy) {
        this.artifactResolverStrategy = strategy;
        return this;
    }

    public WrongConfiguredSerdesTesterBuilder<P> withDataGenerator(SerdesTester.DataGenerator<P> generator) {
        this.dataGenerator = generator;
        return this;
    }

    /**
     * @see TesterBuilder#build()
     */
    @Override
    public Tester build() {
        Objects.requireNonNull(producerProperties);
        Objects.requireNonNull(serializer);
        Objects.requireNonNull(topic);
        Objects.requireNonNull(artifactResolverStrategy);
        Objects.requireNonNull(dataGenerator);
        return new WrongConfiguredSerdesTester();
    }


    private class WrongConfiguredSerdesTester extends SerdesTester<String, P, Object> implements Tester {

        /**
         * @see Tester#test()
         */
        @Override
        public void test() throws Exception {
            Producer<String, P> producer = this.createProducer(producerProperties, StringSerializer.class, serializer, topic, artifactResolverStrategy);

            assertThrows(ExecutionException.class, () -> this.produceMessages(producer, topic, dataGenerator, 10));

        }

    }
}
