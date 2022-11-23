/*
 * Copyright 2021 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.tests.dbupgrade;

import static io.apicurio.tests.utils.CustomTestsUtils.createArtifact;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.apicurio.registry.rest.client.RegistryClientFactory;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.tests.SimpleDisplayName;
import io.apicurio.tests.ApicurioV2BaseIT;
import io.apicurio.tests.common.Constants;
import io.apicurio.tests.common.KafkaFacade;
import io.apicurio.tests.common.RegistryFacade;
import io.apicurio.tests.common.RegistryStorageType;
import io.apicurio.tests.common.interfaces.TestSeparator;
import io.apicurio.tests.common.utils.RegistryUtils;

/**
 * @author Fabian Martinez
 */
@DisplayNameGeneration(SimpleDisplayName.class)
@TestInstance(Lifecycle.PER_CLASS)
@Tag(Constants.DB_UPGRADE)
@Tag(Constants.KAFKA_SQL)
public class KafkaSqlLogCompactionIT implements TestSeparator, Constants {

    final Logger logger = LoggerFactory.getLogger(getClass());


    @Test
    public void testLogCompaction() throws Exception {

        RegistryStorageType previousStorageValue = RegistryUtils.REGISTRY_STORAGE;

        String testName = "testLogCompaction";
        RegistryStorageType storage = RegistryStorageType.kafkasql;

        RegistryUtils.REGISTRY_STORAGE = storage;

        Path logsPath = RegistryUtils.getLogsPath(getClass(), testName);
        RegistryFacade facade = RegistryFacade.getInstance();

        try {

            Map<String, String> appEnv = facade.initRegistryAppEnv();

            //runs all required infra except for the registry
            facade.deployStorage(appEnv, storage);

            //Create the kafkasql-journal topic with aggressive log compaction
            //bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic kafkasql-journal --partitions 1 --replication-factor 1 --config min.cleanable.dirty.ratio=0.000001 --config cleanup.policy=compact --config segment.ms=100 --config delete.retention.ms=100
            var kafkaAdminClient = KafkaFacade.getInstance().adminClient();
            var journal = new NewTopic("kafkasql-journal", 1, (short)1);
            journal.configs(Map.of(
                        "min.cleanable.dirty.ratio","0.000001",
                        "cleanup.policy","compact",
                        "segment.ms", "100",
                        "delete.retention.ms", "100"
                    ));
            kafkaAdminClient.createTopics(Arrays.asList(journal));

            appEnv.put("QUARKUS_HTTP_PORT", "8081");

            String registryNameSuffix = "first-start";
            facade.runRegistry(appEnv, registryNameSuffix, "8081");
            facade.waitForRegistryReady();
            //

            var registryClient = RegistryClientFactory.create("http://localhost:8081");

            createArtifact(registryClient, ArtifactType.AVRO, ApicurioV2BaseIT.resourceToString("artifactTypes/" + "avro/multi-field_v1.json"));
            var artifactdata = createArtifact(registryClient, ArtifactType.JSON, ApicurioV2BaseIT.resourceToString("artifactTypes/" + "jsonSchema/person_v1.json"));
            createArtifact(registryClient, ArtifactType.PROTOBUF, ApicurioV2BaseIT.resourceToString("artifactTypes/" + "protobuf/tutorial_v1.proto"));

            assertEquals(3, registryClient.listArtifactsInGroup(null).getCount());

            //spend some time doing something
            //this is just to give kafka some time to be 100% the topic is log compactedsddddddd
            logger.info("Giving kafka some time to do log compaction");
            for (int i = 0; i<15; i++) {
                registryClient.getArtifactMetaData(artifactdata.meta.getGroupId(), artifactdata.meta.getId());
                Thread.sleep(900);
            }
            logger.info("Finished giving kafka some time");

            //

            facade.stopProcess(logsPath, "registry-" + registryNameSuffix);

            facade.runRegistry(appEnv, "registry-second-start", "8081");
            facade.waitForRegistryReady();

            //

            var searchResults = registryClient.listArtifactsInGroup(null);
            assertEquals(3, searchResults.getCount());

            String test2content = ApicurioV2BaseIT.resourceToString("artifactTypes/" + "protobuf/tutorial_v2.proto");
            String originalHash = DigestUtils.sha256Hex(test2content);
            var artifact = createArtifact(registryClient, ArtifactType.PROTOBUF, test2content);

            assertEquals(originalHash, artifact.contentHash);

            String byglobalidHash = DigestUtils.sha256Hex(registryClient.getContentByGlobalId(artifact.meta.getGlobalId()));
            String bycontentidHash = DigestUtils.sha256Hex(registryClient.getContentById(artifact.meta.getContentId()));

            assertEquals(originalHash, byglobalidHash);
            assertEquals(originalHash, bycontentidHash);

            //assert total num of artifacts
            assertEquals(4, registryClient.listArtifactsInGroup(null).getCount());


        } finally {
            try {
                facade.stopAndCollectLogs(logsPath);
            } finally {
                RegistryUtils.REGISTRY_STORAGE = previousStorageValue;
            }
        }

    }

}
