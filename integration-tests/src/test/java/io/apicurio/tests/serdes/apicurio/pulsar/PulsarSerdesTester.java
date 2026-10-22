package io.apicurio.tests.serdes.apicurio.pulsar;

import io.apicurio.registry.utils.tests.TestUtils;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PulsarSerdesTester<P, C> {

    private static final Logger LOGGER = LoggerFactory.getLogger(PulsarSerdesTester.class);

    private static final int MILLIS_PER_MESSAGE = 700;
    private static final String BOOTSTRAP_SERVERS = "localhost:9092";
    private static final String MAC_OS_BOOTSTRAP_SERVERS = "docker.host.internal:9092";

    private boolean autoClose = true;

    public PulsarSerdesTester() {
        // empty
    }

    public void produceMessages(Producer<P> producer, String topicName, DataGenerator<P> dataGenerator,
            int messageCount, boolean retry) throws Exception {

        if (retry) {
            TestUtils.retry(() -> produceMessages(producer, topicName, dataGenerator, messageCount));
        } else {
            produceMessages(producer, topicName, dataGenerator, messageCount);
        }
    }

    private void produceMessages(Producer<P> producer, String topicName, DataGenerator<P> dataGenerator,
            int messageCount) throws ExecutionException, InterruptedException, TimeoutException {
        CompletableFuture<Integer> resultPromise = CompletableFuture.supplyAsync(() -> {

            int producedMessages = 0;

            try {
                while (producedMessages < messageCount) {
                    P data = dataGenerator.generate(producedMessages);
                    LOGGER.info("Sending message {} to topic {}", data, topicName);
                    MessageId fr = producer.send(data);
                    producedMessages++;

                }
                LOGGER.info("Produced {} messages", producedMessages);

            } catch (PulsarClientException e) {
                throw new RuntimeException(e);
            }

            return producedMessages;
        });

        try {
            Integer messagesSent = resultPromise.get((MILLIS_PER_MESSAGE * messageCount) + 2000,
                    TimeUnit.MILLISECONDS);
            assertEquals(messageCount, messagesSent.intValue());
        } catch (Exception e) {
            throw e;
        }
    }

    public void consumeMessages(Consumer<C> consumer, String topicName, int messageCount,
            Predicate<C> dataValidator) throws Exception {
        CompletableFuture<Integer> resultPromise = CompletableFuture.supplyAsync(() -> {

            AtomicInteger consumedMessages = new AtomicInteger();

            try {
                while (consumedMessages.get() < messageCount) {

                    Message<C> message = consumer.receive();

                    if (dataValidator != null) {
                        assertTrue(dataValidator.test(message.getValue()),
                                "Consumed record validation failed");
                    }

                    consumedMessages.getAndIncrement();
                    LOGGER.info("{} {}", message.getTopicName(), message.getValue());
                }

                LOGGER.info("Consumed {} messages", consumedMessages.get());

            } catch (PulsarClientException e) {
                throw new RuntimeException(e);
            }

            return consumedMessages.get();
        });

        Integer messagesConsumed = resultPromise.get((MILLIS_PER_MESSAGE * messageCount) + 2000,
                TimeUnit.MILLISECONDS);
        assertEquals(messageCount, messagesConsumed.intValue());

    }

    @FunctionalInterface
    public static interface DataGenerator<T> {

        public T generate(int count);

    }

    @FunctionalInterface
    public static interface Validator {

        public boolean validate() throws Exception;

    }
}