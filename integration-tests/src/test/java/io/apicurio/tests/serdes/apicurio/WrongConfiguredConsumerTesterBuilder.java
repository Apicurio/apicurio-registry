package io.apicurio.tests.serdes.apicurio;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Objects;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WrongConfiguredConsumerTesterBuilder<P, C> extends SimpleSerdesTesterBuilder<P, C> {

    /**
     * @see Tester.TesterBuilder#build()
     */
    @Override
    public Tester build() {
        Objects.requireNonNull(producerProperties);
        Objects.requireNonNull(consumerProperties);
        Objects.requireNonNull(serializer);
        Objects.requireNonNull(topic);
        Objects.requireNonNull(artifactResolverStrategy);
        Objects.requireNonNull(dataGenerator);
        Objects.requireNonNull(deserializer);
        Objects.requireNonNull(dataValidator);
        return new FailingConsumerTester();
    }

    private class FailingConsumerTester extends KafkaSerdesTester<String, P, C> implements Tester {

        /**
         * @see Tester#test()
         */
        @Override
        public void test() throws Exception {
            Producer<String, P> producer = this.createProducer(producerProperties, StringSerializer.class,
                    serializer, topic, artifactResolverStrategy);

            int messageCount = 10;
            this.produceMessages(producer, topic, dataGenerator, messageCount, false);

            if (afterProduceValidator != null) {
                assertTrue(afterProduceValidator.validate(), "After produce validation failed");
            }

            Consumer<String, C> consumer = this.createConsumer(consumerProperties, StringDeserializer.class,
                    deserializer, topic);

            assertThrows(ExecutionException.class,
                    () -> this.consumeMessages(consumer, topic, messageCount, dataValidator));
        }

    }

}
