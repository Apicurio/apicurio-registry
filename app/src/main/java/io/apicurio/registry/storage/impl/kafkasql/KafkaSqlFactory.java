package io.apicurio.registry.storage.impl.kafkasql;

import io.apicurio.registry.storage.impl.kafkasql.serde.KafkaSqlKeyDeserializer;
import io.apicurio.registry.storage.impl.kafkasql.serde.KafkaSqlKeySerializer;
import io.apicurio.registry.storage.impl.kafkasql.serde.KafkaSqlValueDeserializer;
import io.apicurio.registry.storage.impl.kafkasql.serde.KafkaSqlValueSerializer;
import io.apicurio.registry.storage.impl.util.AsyncProducer;
import io.apicurio.registry.storage.impl.util.ProducerActions;
import io.apicurio.registry.cdi.LazyResource;
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

import java.util.function.Supplier;

import static io.apicurio.registry.utils.CollectionsUtil.toProperties;

@ApplicationScoped
@LookupIfProperty(name = "apicurio.storage.kind", stringValue = "kafkasql")
public class KafkaSqlFactory {

    @Inject
    Instance<KafkaSqlConfiguration> config;

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
        return new KafkaConsumer<>(toProperties(config.get().getConsumerProperties()), new KafkaSqlKeyDeserializer(), new KafkaSqlValueDeserializer());
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
