package io.apicurio.registry.storage.impl.kafkasql;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.utils.kafka.KafkaUtil;
import io.apicurio.registry.utils.tests.KafkasqlSnapshotTestProfile;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

@QuarkusTest
@TestProfile(KafkasqlSnapshotTestProfile.class)
@QuarkusTestResource(value = KafkaSqlSnapshotTest.KafkaSqlSnapshotTestInitializer.class, restrictToAnnotatedClass = true)
public class KafkaSqlSnapshotTest extends AbstractResourceTestBase {

    @Inject
    KafkaSqlSnapshotManager snapshotManager;

    @Test
    public void testRecoverFromSnapshot() throws InterruptedException {
        snapshotManager.triggerSnapshotCreation();
    }

    protected static class KafkaSqlSnapshotTestInitializer implements QuarkusTestResourceLifecycleManager {

        public KafkaSqlSnapshotTestInitializer() {
        }

        @Override
        public int order() {
            return 10001;
        }

        @Override
        public Map<String, String> start() {

            Properties props = connectionProperties();
            props.putIfAbsent(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString());

            KafkaUtil.createTopics(props, Set.of(), Collections.emptyMap());

            StringSerializer keySerializer = new StringSerializer();
            StringSerializer valueSerializer = new StringSerializer();

            // Create the Kafka Producer
            KafkaProducer<String, String> snapshotsProducer = new KafkaProducer<>(props, keySerializer, valueSerializer);

            String snapshotLocation = "/io/apicurio/registry/storage/impl/kafkasql/1302b402-c707-457e-af76-10c1045e68e8.sql";

            InputStream stream = getClass().getResourceAsStream(snapshotLocation);
            Assertions.assertNotNull(stream);

            ProducerRecord<String, String> record = new ProducerRecord<>("kafkasql-snapshots", 0, "1302b402-c707-457e-af76-10c1045e68e8", snapshotLocation,
                    Collections.emptyList());

            snapshotsProducer.send(record);

            return Collections.emptyMap();
        }

        public Properties connectionProperties() {
            Properties properties = new Properties();
            properties.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, System.getProperty("bootstrap.servers.external"));
            properties.put(CommonClientConfigs.CONNECTIONS_MAX_IDLE_MS_CONFIG, 10000);
            properties.put(CommonClientConfigs.REQUEST_TIMEOUT_MS_CONFIG, 5000);
            return properties;
        }

        @Override
        public void stop() {
        }
    }
}
