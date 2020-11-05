/*
 * Copyright 2020 Red Hat
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

package io.apicurio.tests;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.apache.avro.Schema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.apicurio.registry.client.RegistryRestClient;
import io.apicurio.registry.rest.beans.ArtifactMetaData;
import io.apicurio.registry.rest.beans.EditableMetaData;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.tests.RegistryRestClientExtension;
import io.apicurio.registry.utils.tests.SimpleDisplayName;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.interfaces.TestSeparator;
import io.apicurio.tests.utils.subUtils.ArtifactUtils;

@DisplayNameGeneration(SimpleDisplayName.class)
@ExtendWith(RegistryDeploymentManager.class)
@ExtendWith(RegistryRestClientExtension.class)
public abstract class BaseIT implements TestSeparator, Constants {

    protected static final Logger LOGGER = LoggerFactory.getLogger(BaseIT.class);
    protected static KafkaFacade kafkaCluster = KafkaFacade.getInstance();

    protected final String resourceToString(String resourceName) {
        try (InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourceName)) {
            Assertions.assertNotNull(stream, "Resource not found: " + resourceName);
            return new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @AfterEach
    void cleanArtifacts() throws Exception {
        LOGGER.info("Removing all artifacts");
        String[] artifacts = ArtifactUtils.listArtifacts().getBody().as(String[].class);
        for (String artifactId : artifacts) {
            try {
                ArtifactUtils.deleteArtifact(artifactId);
            } catch (AssertionError e) {
                //because of async storage artifact may be already deleted but listed anyway
                LOGGER.info(e.getMessage());
            } catch (Exception e) {
                LOGGER.error("", e);
            }
        }
    }

    protected Map<String, String> createMultipleArtifacts(RegistryRestClient apicurioService, int count) throws Exception {
        Map<String, String> idMap = new HashMap<>();

        for (int x = 0; x < count; x++) {
            String name = "myrecord" + x;
            String artifactId = TestUtils.generateArtifactId();

            String artifactDefinition = "{\"type\":\"record\",\"name\":\"" + name + "\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";
            ByteArrayInputStream artifactData = new ByteArrayInputStream(artifactDefinition.getBytes(StandardCharsets.UTF_8));
            ArtifactMetaData amd = apicurioService.createArtifact(artifactId, ArtifactType.AVRO, artifactData);

            // Make sure artifact is fully registered
            TestUtils.retry(() -> apicurioService.getArtifactMetaDataByGlobalId(amd.getGlobalId()));

            LOGGER.info("Created record with name: {} and ID: {}", amd.getName(), amd.getId());
            idMap.put(name, amd.getId());
        }

        return idMap;
    }

    protected void deleteMultipleArtifacts(RegistryRestClient apicurioService, Map<String, String> idMap) {
        for (Map.Entry<String, String> entry : idMap.entrySet()) {
            apicurioService.deleteArtifact(entry.getValue());
            LOGGER.info("Deleted artifact {} with ID: {}", entry.getKey(), entry.getValue());
        }
    }

    public void createArtifactViaApicurioClient(RegistryRestClient client, Schema schema, String artifactName) throws TimeoutException {
        ArtifactMetaData artifactMetadata = client.createArtifact(
                artifactName,
                ArtifactType.AVRO,
                new ByteArrayInputStream(schema.toString().getBytes(StandardCharsets.UTF_8))
        );
        EditableMetaData editableMetaData = new EditableMetaData();
        editableMetaData.setName(artifactName);
        client.updateArtifactMetaData(artifactName, editableMetaData);
        // wait for global id store to populate (in case of Kafka / Streams)
        TestUtils.waitFor("Wait until artifact globalID mapping is finished", Constants.POLL_INTERVAL, Constants.TIMEOUT_GLOBAL,
            () -> {
                ArtifactMetaData metadata = client.getArtifactMetaDataByGlobalId(artifactMetadata.getGlobalId());
                LOGGER.info("Checking that created schema is equal to the get schema");
                assertThat(metadata.getName(), is(artifactName));
                return true;
            });
    }

    public void updateArtifactViaApicurioClient(RegistryRestClient client, Schema schema, String artifactName) throws TimeoutException {
        ArtifactMetaData artifactMetadata = client.updateArtifact(
                artifactName,
                ArtifactType.AVRO,
                new ByteArrayInputStream(schema.toString().getBytes(StandardCharsets.UTF_8))
        );
        EditableMetaData editableMetaData = new EditableMetaData();
        editableMetaData.setName(artifactName);
        client.updateArtifactMetaData(artifactName, editableMetaData);
        // wait for global id store to populate (in case of Kafka / Streams)
        TestUtils.waitFor("Wait until artifact globalID mapping is finished", Constants.POLL_INTERVAL, Constants.TIMEOUT_GLOBAL,
            () -> {
                ArtifactMetaData metadata = client.getArtifactMetaDataByGlobalId(artifactMetadata.getGlobalId());
                LOGGER.info("Checking that created schema is equal to the get schema");
                assertThat(metadata.getName(), is(artifactName));
                return true;
            });
    }

    protected String generateArtifactId() {
        return TestUtils.generateArtifactId();
    }
}
