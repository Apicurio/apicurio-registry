package io.apicurio.registry.storage.impl.kafkasql;

import io.apicurio.registry.storage.impl.kafkasql.serde.KafkaSqlKeyDeserializer;
import io.apicurio.registry.storage.impl.kafkasql.serde.KafkaSqlKeySerializer;
import io.apicurio.registry.storage.impl.kafkasql.serde.KafkaSqlValueDeserializer;
import io.apicurio.registry.storage.impl.kafkasql.serde.KafkaSqlValueSerializer;
import io.apicurio.registry.storage.impl.util.AsyncProducer;
import io.apicurio.registry.storage.impl.util.ProducerActions;
import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.cdi.LazyResource;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.kafka.KafkaClientMetrics;
import io.quarkus.arc.lookup.LookupIfProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.BytesDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.common.utils.Bytes;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;

import java.util.function.Supplier;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_OBSERVABILITY;
import static io.apicurio.registry.utils.CollectionsUtil.toProperties;

@ApplicationScoped
@LookupIfProperty(name = "apicurio.storage.kind", stringValue = "kafkasql")
public class KafkaSqlFactory {

    @Inject
    Instance<KafkaSqlConfiguration> config;

    @Inject
    MeterRegistry meterRegistry;

    @Inject
    Logger log;

    @Info(description = """
                    Publish Kafka client metrics, including the journal consumer lag, for KafkaSQL storage.
            """, category = CATEGORY_OBSERVABILITY, availableSince = "3.3.2")
    @ConfigProperty(name = "apicurio.metrics.kafka.enabled", defaultValue = "true")
    boolean kafkaMetricsEnabled;

    /**
     * Held so that the binder, and through it the meters it registered, is not collected while the consumer
     * is still in use.
     */
    private KafkaClientMetrics journalConsumerMetrics;

    @Produces
    @ApplicationScoped
    @Named("KafkaSqlJournalProducer")
    @LookupIfProperty(name = "apicurio.storage.kind", stringValue = "kafkasql")
    public ProducerActions<KafkaSqlMessageKey, KafkaSqlMessage> createKafkaJournalProducer() {
        return new AsyncProducer<>(toProperties(config.get().getProducerProperties()), new KafkaSqlKeySerializer(), new KafkaSqlValueSerializer());
    }

    @Produces
    @ApplicationScoped
    @Named("KafkaSqlJournalConsumer")
    @LookupIfProperty(name = "apicurio.storage.kind", stringValue = "kafkasql")
    public KafkaConsumer<KafkaSqlMessageKey, KafkaSqlMessage> createKafkaJournalConsumer() {
        var consumer = new KafkaConsumer<>(toProperties(config.get().getConsumerProperties()), new KafkaSqlKeyDeserializer(), new KafkaSqlValueDeserializer());
        journalConsumerMetrics = bindKafkaMetrics(consumer);
        return consumer;
    }

    /**
     * Exposes the Kafka client's own metrics, most usefully the journal consumer lag, which tells an operator
     * how far behind this replica is in applying the journal. The consumers here are constructed directly
     * rather than through a Quarkus extension, so nothing binds them automatically.
     */
    private KafkaClientMetrics bindKafkaMetrics(KafkaConsumer<?, ?> consumer) {
        if (!kafkaMetricsEnabled) {
            return null;
        }
        try {
            var metrics = new KafkaClientMetrics(consumer);
            metrics.bindTo(meterRegistry);
            return metrics;
        } catch (Exception ex) {
            // Reporting is best effort and must not stop storage from starting.
            log.warn("Could not publish Kafka client metrics: {}", ex.getMessage());
            return null;
        }
    }

    @Produces
    @ApplicationScoped
    @LookupIfProperty(name = "apicurio.storage.kind", stringValue = "kafkasql")
    public KafkaSqlVerificationJournalConsumer createVerificationKafkaJournalConsumer() {
        return new KafkaSqlVerificationJournalConsumer(() -> new KafkaConsumer<>(toProperties(config.get().getConsumerProperties()), new BytesDeserializer(), new BytesDeserializer()));
    }

    public static final class KafkaSqlVerificationJournalConsumer extends LazyResource<KafkaConsumer<Bytes, Bytes>> {

        public KafkaSqlVerificationJournalConsumer(Supplier<KafkaConsumer<Bytes, Bytes>> create) {
            super(create, null);
        }
    }

    @Produces
    @ApplicationScoped
    @Named("KafkaSqlSnapshotsProducer")
    @LookupIfProperty(name = "apicurio.storage.kind", stringValue = "kafkasql")
    public ProducerActions<String, String> createKafkaSnapshotsProducer() {
        return new AsyncProducer<>(toProperties(config.get().getProducerProperties()), new StringSerializer(), new StringSerializer());
    }

    @Produces
    @ApplicationScoped
    @Named("KafkaSqlSnapshotsConsumer")
    @LookupIfProperty(name = "apicurio.storage.kind", stringValue = "kafkasql")
    public KafkaConsumer<String, String> createKafkaSnapshotsConsumer() {
        return new KafkaConsumer<>(toProperties(config.get().getConsumerProperties()), new StringDeserializer(), new StringDeserializer());
    }

    @Produces
    @ApplicationScoped
    @Named("KafkaSqlEventsProducer")
    @LookupIfProperty(name = "apicurio.storage.kind", stringValue = "kafkasql")
    public ProducerActions<String, String> createKafkaSqlEventsProducer() {
        return new AsyncProducer<>(toProperties(config.get().getProducerProperties()), new StringSerializer(), new StringSerializer());
    }


    public static final class KafkaSqlJournalConsumer extends LazyResource<Admin> {

        public KafkaSqlJournalConsumer(Supplier<Admin> create) {
            super(create, null);
        }
    }

    @Produces
    @ApplicationScoped
    @LookupIfProperty(name = "apicurio.storage.kind", stringValue = "kafkasql")
    public KafkaAdminClient createKafkaAdminClient() {
        return new KafkaAdminClient(() -> Admin.create(toProperties(config.get().getAdminProperties())));
    }

    public static final class KafkaAdminClient extends LazyResource<Admin> {

        public KafkaAdminClient(Supplier<Admin> create) {
            super(create, null);
        }
    }
}
