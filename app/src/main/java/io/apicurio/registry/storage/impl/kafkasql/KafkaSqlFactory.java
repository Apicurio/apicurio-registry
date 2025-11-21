package io.apicurio.registry.storage.impl.kafkasql;

import io.apicurio.registry.storage.impl.kafkasql.serde.KafkaSqlKeyDeserializer;
import io.apicurio.registry.storage.impl.kafkasql.serde.KafkaSqlKeySerializer;
import io.apicurio.registry.storage.impl.kafkasql.serde.KafkaSqlValueDeserializer;
import io.apicurio.registry.storage.impl.kafkasql.serde.KafkaSqlValueSerializer;
import io.apicurio.registry.storage.impl.util.AsyncProducer;
import io.apicurio.registry.storage.impl.util.ProducerActions;
import io.apicurio.registry.types.LazyResource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.BytesDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.common.utils.Bytes;

import java.util.function.Supplier;

import static io.apicurio.registry.utils.CollectionsUtil.toProperties;

@ApplicationScoped
public class KafkaSqlFactory {

    @Inject
    KafkaSqlConfiguration config;

    @Produces
    @ApplicationScoped
    @Named("KafkaSqlJournalProducer")
    public ProducerActions<KafkaSqlMessageKey, KafkaSqlMessage> createKafkaJournalProducer() {
        return new AsyncProducer<>(toProperties(config.getProducerProperties()), new KafkaSqlKeySerializer(), new KafkaSqlValueSerializer());
    }

    @Produces
    @ApplicationScoped
    @Named("KafkaSqlJournalConsumer")
    public KafkaConsumer<KafkaSqlMessageKey, KafkaSqlMessage> createKafkaJournalConsumer() {
        return new KafkaConsumer<>(toProperties(config.getConsumerProperties()), new KafkaSqlKeyDeserializer(), new KafkaSqlValueDeserializer());
    }

    @Produces
    @ApplicationScoped
    public KafkaSqlVerificationJournalConsumer createVerificationKafkaJournalConsumer() {
        return new KafkaSqlVerificationJournalConsumer(() -> new KafkaConsumer<>(toProperties(config.getConsumerProperties()), new BytesDeserializer(), new BytesDeserializer()));
    }

    public static final class KafkaSqlVerificationJournalConsumer extends LazyResource<KafkaConsumer<Bytes, Bytes>> {

        public KafkaSqlVerificationJournalConsumer(Supplier<KafkaConsumer<Bytes, Bytes>> create) {
            super(create, null);
        }
    }

    @Produces
    @ApplicationScoped
    @Named("KafkaSqlSnapshotsProducer")
    public ProducerActions<String, String> createKafkaSnapshotsProducer() {
        return new AsyncProducer<>(toProperties(config.getProducerProperties()), new StringSerializer(), new StringSerializer());
    }

    @Produces
    @ApplicationScoped
    @Named("KafkaSqlSnapshotsConsumer")
    public KafkaConsumer<String, String> createKafkaSnapshotsConsumer() {
        return new KafkaConsumer<>(toProperties(config.getConsumerProperties()), new StringDeserializer(), new StringDeserializer());
    }

    @Produces
    @ApplicationScoped
    @Named("KafkaSqlEventsProducer")
    public ProducerActions<String, String> createKafkaSqlEventsProducer() {
        return new AsyncProducer<>(toProperties(config.getProducerProperties()), new StringSerializer(), new StringSerializer());
    }


    public static final class KafkaSqlJournalConsumer extends LazyResource<Admin> {

        public KafkaSqlJournalConsumer(Supplier<Admin> create) {
            super(create, null);
        }
    }

    @Produces
    @ApplicationScoped
    public KafkaAdminClient createKafkaAdminClient() {
        return new KafkaAdminClient(() -> Admin.create(toProperties(config.getAdminProperties())));
    }

    public static final class KafkaAdminClient extends LazyResource<Admin> {

        public KafkaAdminClient(Supplier<Admin> create) {
            super(create, null);
        }
    }
}
