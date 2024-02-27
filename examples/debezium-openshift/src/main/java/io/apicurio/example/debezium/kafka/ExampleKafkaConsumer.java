package io.apicurio.example.debezium.kafka;

import io.apicurio.example.debezium.Operation;
import io.apicurio.example.debezium.model.*;
import io.quarkus.runtime.StartupEvent;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * @author Jakub Senko <em>m@jsenko.net</em>
 */
@ApplicationScoped
public class ExampleKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(ExampleKafkaConsumer.class);

    @Inject
    KafkaFactory kafkaFactory;


    void onStart(@Observes StartupEvent event) {

        Runnable runner = () -> {
            try (KafkaConsumer<Object, Object> consumer = kafkaFactory.createKafkaConsumer()) {

                var topics = List.of(
                        "example.inventory.addresses",
                        "example.inventory.customers",
                        "example.inventory.orders",
                        "example.inventory.products",
                        "example.inventory.products_on_hand"
                );
                var existingTopic = consumer.listTopics().keySet();
                if (!existingTopic.containsAll(topics)) {
                    throw new IllegalStateException("Some topics are not available. " +
                            "Expected: " + topics + ", actual: " + existingTopic);
                }

                consumer.subscribe(topics);

                while (true) {
                    try {
                        var records = consumer.poll(Duration.of(10, ChronoUnit.SECONDS));
                        if (records != null && !records.isEmpty()) {
                            log.info("Consuming {} records:", records.count());
                            records.forEach(record -> {
                                if (record.key() == null) {
                                    log.debug("Discarded an unknown message");
                                    return;
                                }
                                if (record.value() == null) {
                                    log.debug("Discarded a tombstone message");
                                    return;
                                }

                                log.info("---");
                                log.info("Raw key: {}", record.key());
                                log.info("Raw key schema: {}", ((SpecificRecord) record.key()).getSchema());
                                log.info("Raw value: {}", record.value());
                                log.info("Raw value schema: {}", ((SpecificRecord) record.value()).getSchema());

                                switch (record.topic()) {
                                    case "example.inventory.addresses": {
                                        var key = (example.inventory.addresses.Key) record.key();
                                        var value = (example.inventory.addresses.Envelope) record.value();
                                        log.info("Operation {} on Address", Operation.from(value.getOp()));
                                        log.info("ID: {}", key.getId());
                                        log.info("Before: {}", Address.from(value.getBefore()));
                                        log.info("After: {}", Address.from(value.getAfter()));
                                        break;
                                    }
                                    case "example.inventory.customers": {
                                        var key = (example.inventory.customers.Key) record.key();
                                        var value = (example.inventory.customers.Envelope) record.value();
                                        log.info("Operation {} on Customer", Operation.from(value.getOp()));
                                        log.info("ID: {}", key.getId());
                                        log.info("Before: {}", Customer.from(value.getBefore()));
                                        log.info("After: {}", Customer.from(value.getAfter()));
                                        break;
                                    }
                                    case "example.inventory.orders": {
                                        var key = (example.inventory.orders.Key) record.key();
                                        var value = (example.inventory.orders.Envelope) record.value();
                                        log.info("Operation {} on Order", Operation.from(value.getOp()));
                                        log.info("Order number: {}", key.getOrderNumber());
                                        log.info("Before: {}", Order.from(value.getBefore()));
                                        log.info("After: {}", Order.from(value.getAfter()));
                                        break;
                                    }
                                    case "example.inventory.products": {
                                        var key = (example.inventory.products.Key) record.key();
                                        var value = (example.inventory.products.Envelope) record.value();
                                        log.info("Operation {} on Product", Operation.from(value.getOp()));
                                        log.info("ID: {}", key.getId());
                                        log.info("Before: {}", Product.from(value.getBefore()));
                                        log.info("After: {}", Product.from(value.getAfter()));
                                        break;
                                    }
                                    case "example.inventory.products_on_hand": {
                                        var key = (example.inventory.products_on_hand.Key) record.key();
                                        var value = (example.inventory.products_on_hand.Envelope) record.value();
                                        log.info("Operation {} on ProductOnHand", Operation.from(value.getOp()));
                                        log.info("Product ID: {}", key.getProductId());
                                        log.info("Before: {}", ProductOnHand.from(value.getBefore()));
                                        log.info("After: {}", ProductOnHand.from(value.getAfter()));
                                        break;
                                    }
                                    default:
                                        throw new IllegalStateException("Received a message from unexpected topic: " + record.topic());
                                }
                            });
                        }
                    } catch (Exception ex) {
                        log.error("Error reading records from Kafka", ex);
                    }
                }
            }
        };
        var thread = new Thread(runner);
        thread.setDaemon(true);
        thread.setName("Kafka Consumer Thread");
        thread.start();
    }
}
