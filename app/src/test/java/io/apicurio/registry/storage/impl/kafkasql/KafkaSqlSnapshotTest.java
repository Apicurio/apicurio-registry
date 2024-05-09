package io.apicurio.registry.storage.impl.kafkasql;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.ArtifactContent;
import io.apicurio.registry.utils.kafka.KafkaUtil;
import io.apicurio.registry.utils.tests.KafkasqlSnapshotTestProfile;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import lombok.SneakyThrows;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@QuarkusTest
@TestProfile(KafkasqlSnapshotTestProfile.class)
@QuarkusTestResource(value = KafkaSqlSnapshotTest.KafkaSqlSnapshotTestInitializer.class, restrictToAnnotatedClass = true)
public class KafkaSqlSnapshotTest extends AbstractResourceTestBase {

    private static final String NEW_ARTIFACTS_SNAPSHOT_TEST_GROUP_ID = "SNAPSHOT_TEST_GROUP_ID";

    @BeforeAll
    public void init() {
        //Create a bunch of artifacts so they're added on top of the snapshot
        String simpleAvro = resourceToString("avro.json");
/*
        for (int idx = 0; idx < 1000; idx++) {
            System.out.println("Iteration: " + idx);
            String artifactId = UUID.randomUUID().toString();
            ArtifactContent content = new ArtifactContent();
            content.setContent(simpleAvro);
            clientV3.groups().byGroupId(NEW_ARTIFACTS_SNAPSHOT_TEST_GROUP_ID).artifacts().post(content, config -> {
                config.headers.add("X-Registry-ArtifactId", artifactId);
            });
            clientV3.groups().byGroupId(NEW_ARTIFACTS_SNAPSHOT_TEST_GROUP_ID).artifacts().byArtifactId(artifactId).delete();
        }
        */

    }

    @Test
    public void testRecoverFromSnapshot() throws InterruptedException {
        Assertions.assertEquals(4001, clientV3.groups().byGroupId("default").artifacts().get().getCount());
    }

    protected static class KafkaSqlSnapshotTestInitializer implements QuarkusTestResourceLifecycleManager {

        public KafkaSqlSnapshotTestInitializer() {
        }

        @Override
        public int order() {
            return 10001;
        }

        @Override
        @SneakyThrows
        public Map<String, String> start() {
            Properties props = connectionProperties();
            props.putIfAbsent(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString());

            KafkaUtil.createTopics(props, Set.of("kafkasql-snapshots", "kafkasql-journal"), Collections.emptyMap());

            prepareSnapshotMarker(props);
            prepareSnapshotMessages(props);

            return Collections.emptyMap();
        }

        private void prepareSnapshotMarker(Properties props) throws ExecutionException, InterruptedException {
            StringSerializer keySerializer = new StringSerializer();
            StringSerializer valueSerializer = new StringSerializer();

            //Create the data producer to send a snapshot marker
            KafkaProducer<String, String> dataProducer = new KafkaProducer<>(props, keySerializer, valueSerializer);
            ProducerRecord<String, String> snapshotMarkerRecord = new ProducerRecord<>("kafkasql-journal", 0,
                    "{\"uuid\":\"1302b402-c707-457e-af76-10c1045e68e8\",\"messageType\":\"CreateSnapshot1Message\",\"partitionKey\":\"__GLOBAL_PARTITION__\"}", "{\n"
                    + "                \"snapshotLocation\": \"/io/apicurio/registry/storage/impl/kafkasql/1302b402-c707-457e-af76-10c1045e68e8.sql\",\n"
                    + "                    \"key\": {\n"
                    + "                \"uuid\": \"1302b402-c707-457e-af76-10c1045e68e8\",\n"
                    + "                        \"messageType\": \"CreateSnapshot1Message\",\n"
                    + "                        \"partitionKey\": \"__GLOBAL_PARTITION__\"\n"
                    + "            }\n"
                    + "            }", Collections.emptyList());

            //Send snapshot marker
            dataProducer.send(snapshotMarkerRecord).get();
        }

        private void prepareSnapshotMessages(Properties props) throws URISyntaxException, ExecutionException, InterruptedException {
            StringSerializer keySerializer = new StringSerializer();
            StringSerializer valueSerializer = new StringSerializer();

            URL resource = getClass().getResource("/io/apicurio/registry/storage/impl/kafkasql/1302b402-c707-457e-af76-10c1045e68e8.sql");
            String snapshotLocation = Paths.get(resource.toURI()).toFile().getAbsolutePath();

            //Send three messages to the snapshots topic, two invalid, and one valid. Only the latest valid one must be processed.
            ProducerRecord<String, String> olderInvalidSnapshot = new ProducerRecord<>("kafkasql-snapshots", 0, "1312b402-c707-457e-af76-10c1045e68e8",
                    "snapshotLocation",
                    Collections.emptyList());
            ProducerRecord<String, String> record = new ProducerRecord<>("kafkasql-snapshots", 0, "1302b402-c707-457e-af76-10c1045e68e8", snapshotLocation,
                    Collections.emptyList());
            ProducerRecord<String, String> newerInvalidSnaphot = new ProducerRecord<>("kafkasql-snapshots", 0, "1322b402-c707-457e-af76-10c1045e68e8", "",
                    Collections.emptyList());

            // Create the Kafka Producer
            KafkaProducer<String, String> snapshotsProducer = new KafkaProducer<>(props, keySerializer, valueSerializer);

            snapshotsProducer.send(olderInvalidSnapshot).get();
            snapshotsProducer.send(record).get();
            snapshotsProducer.send(newerInvalidSnaphot).get();
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
