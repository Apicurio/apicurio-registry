package io.apicurio.tests.dbupgrade.kafka;

import io.apicurio.registry.rest.client.RegistryClientFactory;
import io.apicurio.registry.test.utils.KafkaTestContainerManager;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.tests.SimpleDisplayName;
import io.apicurio.tests.ApicurioRegistryBaseIT;
import io.apicurio.tests.dbupgrade.UpgradeTestsDataInitializer;
import io.apicurio.tests.utils.Constants;
import io.apicurio.tests.utils.CustomTestsUtils;
import io.apicurio.tests.utils.TestSeparator;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.apicurio.tests.dbupgrade.UpgradeTestsDataInitializer.PREPARE_AVRO_GROUP;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayNameGeneration(SimpleDisplayName.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag(Constants.DB_UPGRADE)
@Tag(Constants.KAFKA_SQL)
@QuarkusTestResource(value = KafkaTestContainerManager.class, restrictToAnnotatedClass = true)
@QuarkusTestResource(value = KafkaSqlAvroUpgraderIT.KafkaSqlAvroUpgraderInitializer.class, restrictToAnnotatedClass = true)
@QuarkusIntegrationTest
public class KafkaSqlAvroUpgraderIT extends ApicurioRegistryBaseIT implements TestSeparator, Constants {

    static final Logger logger = LoggerFactory.getLogger(KafkaSqlLogCompactionIT.class);
    public static CustomTestsUtils.ArtifactData avroData;

    @Override
    public void cleanArtifacts() throws Exception {
        //Don't clean artifacts for this test
    }

    @Test
    public void testStorageUpgradeAvroUpgraderKafkaSql() throws Exception {
        //The check must be retried so the kafka storage has been bootstrapped
        retry(() -> assertEquals(3, registryClient.listArtifactsInGroup(PREPARE_AVRO_GROUP).getCount()));

        var searchResults = registryClient.listArtifactsInGroup(PREPARE_AVRO_GROUP);

        var avros = searchResults.getArtifacts().stream()
                .filter(ar -> ar.getType().equals(ArtifactType.AVRO))
                .collect(Collectors.toList());

        System.out.println("Avro artifacts are " + avros.size());
        assertEquals(1, avros.size());
        var avroMetadata = registryClient.getArtifactMetaData(avros.get(0).getGroupId(), avros.get(0).getId());
        var content = registryClient.getContentByGlobalId(avroMetadata.getGlobalId());

        //search with canonicalize
        var versionMetadata = registryClient.getArtifactVersionMetaDataByContent(avros.get(0).getGroupId(), avros.get(0).getId(), true, null, content);
        assertEquals(avroData.meta.getContentId(), versionMetadata.getContentId());

        String test1content = ApicurioRegistryBaseIT.resourceToString("artifactTypes/" + "avro/multi-field_v1.json");

        //search with canonicalize
        versionMetadata = registryClient.getArtifactVersionMetaDataByContent(avros.get(0).getGroupId(), avros.get(0).getId(), true, null, IoUtil.toStream(test1content));
        assertEquals(avroData.meta.getContentId(), versionMetadata.getContentId());

        //search without canonicalize
        versionMetadata = registryClient.getArtifactVersionMetaDataByContent(avros.get(0).getGroupId(), avros.get(0).getId(), false, null, IoUtil.toStream(test1content));
        assertEquals(avroData.meta.getContentId(), versionMetadata.getContentId());

        //create one more avro artifact and verify
        String test2content = ApicurioRegistryBaseIT.resourceToString("artifactTypes/" + "avro/multi-field_v2.json");
        avroData = CustomTestsUtils.createArtifact(registryClient, PREPARE_AVRO_GROUP, ArtifactType.AVRO, test2content);
        versionMetadata = registryClient.getArtifactVersionMetaDataByContent(PREPARE_AVRO_GROUP, avroData.meta.getId(), true, null, IoUtil.toStream(test2content));
        assertEquals(avroData.meta.getContentId(), versionMetadata.getContentId());

        //assert total num of artifacts
        assertEquals(4, registryClient.listArtifactsInGroup(PREPARE_AVRO_GROUP).getCount());
    }

    public static class KafkaSqlAvroUpgraderInitializer implements QuarkusTestResourceLifecycleManager {
        private GenericContainer genericContainer;
        @Override
        public Map<String, String> start() {
            if (!Boolean.parseBoolean(System.getProperty("cluster.tests"))) {

                String bootstrapServers = System.getProperty("bootstrap.servers.internal");
                startOldRegistryVersion("quay.io/apicurio/apicurio-registry-kafkasql:2.3.0.Final", bootstrapServers);

                try {
                    var registryClient = RegistryClientFactory.create("http://localhost:8081/");
                    UpgradeTestsDataInitializer.prepareAvroCanonicalHashUpgradeData(registryClient);
                } catch (Exception e) {
                    logger.warn("Error filling old registry with information: ", e);
                }
            }
            return Collections.emptyMap();
        }

        @Override
        public int order() {
            return 10000;
        }

        @Override
        public void stop() {
            if (genericContainer != null && genericContainer.isRunning()) {
                genericContainer.stop();
            }
        }

        private void startOldRegistryVersion(String registryImage, String bootstrapServers) {
            genericContainer = new GenericContainer<>(registryImage)
                    .withEnv(Map.of("KAFKA_BOOTSTRAP_SERVERS", bootstrapServers, "QUARKUS_HTTP_PORT", "8081"))
                    .withExposedPorts(8081)
                    .withNetwork(Network.SHARED);

            genericContainer.setPortBindings(List.of("8081:8081"));

            genericContainer.waitingFor(Wait.forHttp("/apis/registry/v2/search/artifacts").forStatusCode(200));
            genericContainer.start();
        }
    }
}
