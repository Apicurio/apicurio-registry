package io.apicurio.registry.storage.impl.kafkasql;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateRule;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.kafka.KafkaUtil;
import io.apicurio.registry.utils.tests.KafkasqlRecoverFromSnapshotTestProfile;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import lombok.SneakyThrows;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@QuarkusTest
@TestProfile(KafkasqlRecoverFromSnapshotTestProfile.class)
@QuarkusTestResource(value = KafkasqlRecoverFromSnapshotTest.KafkaSqlSnapshotTestInitializer.class, restrictToAnnotatedClass = true)
public class KafkasqlRecoverFromSnapshotTest extends AbstractResourceTestBase {

    private static final String NEW_ARTIFACTS_SNAPSHOT_TEST_GROUP_ID = "SNAPSHOT_TEST_GROUP_ID";

    @BeforeAll
    public void init() {
        //Create a bunch of artifacts and rules, so they're added on top of the snapshot.
        String simpleAvro = resourceToString("avro.json");

        for (int idx = 0; idx < 1000; idx++) {
            System.out.println("Iteration: " + idx);
            String artifactId = UUID.randomUUID().toString();
            CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, ArtifactType.AVRO, simpleAvro,
                    ContentTypes.APPLICATION_JSON);
            clientV3.groups().byGroupId(NEW_ARTIFACTS_SNAPSHOT_TEST_GROUP_ID).artifacts().post(createArtifact, config -> config.headers.add("X-Registry-ArtifactId", artifactId));
            CreateRule createRule = new CreateRule();
            createRule.setRuleType(RuleType.VALIDITY);
            createRule.setConfig("SYNTAX_ONLY");
            clientV3.groups().byGroupId(NEW_ARTIFACTS_SNAPSHOT_TEST_GROUP_ID).artifacts().byArtifactId(artifactId).rules().post(createRule);        }
    }

    @Test
    public void testRecoverFromSnapshot() throws InterruptedException {
        //We expect 4001 artifacts coming from the snapshot
        Assertions.assertEquals(1000, clientV3.groups().byGroupId("default").artifacts().get().getCount());
        //We expect another 1000 artifacts coming added on top of the snapshot
        Assertions.assertEquals(1000, clientV3.groups().byGroupId(NEW_ARTIFACTS_SNAPSHOT_TEST_GROUP_ID).artifacts().get().getCount());
    }

    protected static class KafkaSqlSnapshotTestInitializer implements QuarkusTestResourceLifecycleManager {

        KafkaProducer<String, String> dataProducer;
        KafkaProducer<String, String> snapshotsProducer;

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

            KafkaUtil.createTopics(props, Set.of("kafkasql-snapshots", "kafkasql-journal"), Collections.emptyMap());

            prepareSnapshotMarker(props);
            prepareSnapshotMessages(props);

            return Collections.emptyMap();
        }

        private void prepareSnapshotMarker(Properties props) throws ExecutionException, InterruptedException {
            StringSerializer keySerializer = new StringSerializer();
            StringSerializer valueSerializer = new StringSerializer();

            //Create the data producer to send a snapshot marker
            dataProducer = new KafkaProducer<>(props, keySerializer, valueSerializer);
            RecordHeader messageTypeHeader = new RecordHeader("mt", "CreateSnapshot1Message".getBytes(StandardCharsets.UTF_8));
            ProducerRecord<String, String> snapshotMarkerRecord = new ProducerRecord<>("kafkasql-journal", 0,
                    "{\"uuid\":\"1345b402-c707-457e-af76-10c1045e68e8\",\"messageType\":\"CreateSnapshot1Message\",\"partitionKey\":\"__GLOBAL_PARTITION__\"}", "{\n"
                    + "                \"snapshotLocation\": \"/io/apicurio/registry/storage/impl/kafkasql/943e6945-5aef-4ca0-a3cd-31af380840ea.sql\",\n"
                    + "                \"snapshotId\": \"943e6945-5aef-4ca0-a3cd-31af380840ea\",\n"
                    + "                    \"key\": {\n"
                    + "                \"uuid\": \"943e6945-5aef-4ca0-a3cd-31af380840ea\",\n"
                    + "                        \"messageType\": \"CreateSnapshot1Message\",\n"
                    + "                        \"partitionKey\": \"__GLOBAL_PARTITION__\"\n"
                    + "            }\n"
                    + "            }", List.of(messageTypeHeader));

            //Send snapshot marker
            dataProducer.send(snapshotMarkerRecord).get();
        }

        private void prepareSnapshotMessages(Properties props) throws URISyntaxException, ExecutionException, InterruptedException {
            StringSerializer keySerializer = new StringSerializer();
            StringSerializer valueSerializer = new StringSerializer();

            URL resource = getClass().getResource("/io/apicurio/registry/storage/impl/kafkasql/943e6945-5aef-4ca0-a3cd-31af380840ea.sql");
            String snapshotLocation = Paths.get(resource.toURI()).toFile().getAbsolutePath();

            //Send three messages to the snapshots topic, two invalid, and one valid. Only the latest valid one must be processed.
            ProducerRecord<String, String> olderInvalidSnapshot = new ProducerRecord<>("kafkasql-snapshots", 0, "1312b402-c707-457e-af76-10c1045e68e8",
                    "snapshotLocation",
                    Collections.emptyList());
            ProducerRecord<String, String> record = new ProducerRecord<>("kafkasql-snapshots", 0, "943e6945-5aef-4ca0-a3cd-31af380840ea", snapshotLocation,
                    Collections.emptyList());
            ProducerRecord<String, String> newerInvalidSnaphot = new ProducerRecord<>("kafkasql-snapshots", 0, "1322b402-c707-457e-af76-10c1045e68e8", "",
                    Collections.emptyList());

            // Create the Kafka Producer
            snapshotsProducer = new KafkaProducer<>(props, keySerializer, valueSerializer);

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
            dataProducer.close();
            snapshotsProducer.close();
        }
    }
}
