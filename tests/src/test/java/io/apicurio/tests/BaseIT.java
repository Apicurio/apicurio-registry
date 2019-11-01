/*
 * Copyright 2019 Red Hat
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

import io.apicurio.registry.client.RegistryClient;
import io.apicurio.registry.client.RegistryService;
import io.apicurio.registry.rest.beans.ArtifactMetaData;
import io.apicurio.registry.rest.beans.EditableMetaData;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.ConcurrentUtil;
import io.apicurio.tests.interfaces.TestSeparator;
import io.apicurio.tests.utils.subUtils.TestUtils;
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import io.restassured.RestAssured;
import io.restassured.parsing.Parser;
import org.apache.avro.Schema;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public abstract class BaseIT implements TestSeparator, Constants {

    private static final Logger LOGGER = LoggerFactory.getLogger(BaseIT.class);
    protected static KafkaFacade kafkaCluster = new KafkaFacade();
    private static RegistryFacade registry = new RegistryFacade();

    protected static SchemaRegistryClient confluentService;
    protected static RegistryService apicurioService;

    @BeforeAll
    static void beforeAll() throws Exception {
        if (!RegistryFacade.REGISTRY_URL.equals(RegistryFacade.DEFAULT_REGISTRY_URL) || RegistryFacade.EXTERNAL_REGISTRY.equals("")) {
            registry.start();
        } else {
            LOGGER.info("Going to use already running registries on {}:{}", RegistryFacade.REGISTRY_URL, RegistryFacade.REGISTRY_PORT);
        }
        TestUtils.waitFor("Cannot connect to registries on " + RegistryFacade.REGISTRY_URL + ":" + RegistryFacade.REGISTRY_PORT + " in timeout!",
                Constants.POLL_INTERVAL, Constants.TIMEOUT_FOR_REGISTRY_START_UP, RegistryFacade::isReachable);
        RestAssured.baseURI = "http://" + RegistryFacade.REGISTRY_URL + ":" + RegistryFacade.REGISTRY_PORT;
        LOGGER.info("Registry app is running on {}:{}", RegistryFacade.REGISTRY_URL, RegistryFacade.REGISTRY_PORT);
        RestAssured.defaultParser = Parser.JSON;
        confluentService = new CachedSchemaRegistryClient("http://" + RegistryFacade.REGISTRY_URL + ":" + RegistryFacade.REGISTRY_PORT + "/confluent", 3);
        apicurioService = RegistryClient.create("http://"  + RegistryFacade.REGISTRY_URL + ":" + RegistryFacade.REGISTRY_PORT);
    }

    @AfterAll
    static void afterAll(TestInfo info) throws InterruptedException {
        registry.stop();
        Thread.sleep(3000);
        storeRegistryLog(info.getTestClass().get().getCanonicalName());
    }

    private static void storeRegistryLog(String className) {
        final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm");
        simpleDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        String currentDate = simpleDateFormat.format(Calendar.getInstance().getTime());
        File logDir = new File("target/logs/" + className + "-" + currentDate);

        if (!logDir.exists()) {
            logDir.mkdirs();
        }

        TestUtils.writeFile(logDir + "/registries-stdout.log", registry.getRegistryStdOut());
        TestUtils.writeFile(logDir + "/registries-stderr.log", registry.getRegistryStdErr());
    }

    protected Map<String, String> createMultipleArtifacts(int count) {
        Map<String, String> idMap = new HashMap<>();

        for (int x = 0; x < count; x++) {
            String name = "myrecord" + x;
            String artifactId = String.valueOf(x);

            String artifactDefinition = "{\"type\":\"record\",\"name\":\"" + name + "\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";
            ByteArrayInputStream artifactData = new ByteArrayInputStream(artifactDefinition.getBytes());
            CompletionStage<ArtifactMetaData> csResult = apicurioService.createArtifact(ArtifactType.AVRO, artifactId, artifactData);
            ConcurrentUtil.result(csResult);

            ArtifactMetaData artifactMetaData = apicurioService.getArtifactMetaData(artifactId);

            LOGGER.info("Created record with name: {} and ID: {}", artifactMetaData.getName(), artifactMetaData.getId());
            idMap.put(name, artifactMetaData.getId());
        }

        return idMap;
    }

    protected void deleteMultipleArtifacts(Map<String, String> idMap) {
        for (Map.Entry entry : idMap.entrySet()) {
            apicurioService.deleteArtifact(entry.getValue().toString());
            LOGGER.info("Deleted artifact {} with ID: {}", entry.getKey(), entry.getValue());
        }
    }

    public void createArtifactViaApicurioClient(Schema schema, String artifactName) throws TimeoutException {
        CompletionStage<ArtifactMetaData> csa = apicurioService.createArtifact(
                ArtifactType.AVRO,
                artifactName,
                new ByteArrayInputStream(schema.toString().getBytes())
        );
        ArtifactMetaData artifactMetadata = ConcurrentUtil.result(csa);
        EditableMetaData editableMetaData = new EditableMetaData();
        editableMetaData.setName(artifactName);
        apicurioService.updateArtifactMetaData(artifactName, editableMetaData);
        // wait for global id store to populate (in case of Kafka / Streams)
        TestUtils.waitFor("Wait until artifact globalID mapping is finished", Constants.POLL_INTERVAL, Constants.TIMEOUT_GLOBAL,
            () -> {
                ArtifactMetaData metadata = apicurioService.getArtifactMetaDataByGlobalId(artifactMetadata.getGlobalId());
                LOGGER.info("Checking that created schema is equal to the get schema");
                assertThat(metadata.getName(), is(artifactName));
                return true;
            });
    }

    public void updateArtifactViaApicurioClient(Schema schema, String artifactName) throws TimeoutException {
        CompletionStage<ArtifactMetaData> csa = apicurioService.updateArtifact(
                artifactName,
                ArtifactType.AVRO,
                new ByteArrayInputStream(schema.toString().getBytes())
        );
        ArtifactMetaData artifactMetadata = ConcurrentUtil.result(csa);
        EditableMetaData editableMetaData = new EditableMetaData();
        editableMetaData.setName(artifactName);
        apicurioService.updateArtifactMetaData(artifactName, editableMetaData);
        // wait for global id store to populate (in case of Kafka / Streams)
        TestUtils.waitFor("Wait until artifact globalID mapping is finished", Constants.POLL_INTERVAL, Constants.TIMEOUT_GLOBAL,
            () -> {
                ArtifactMetaData metadata = apicurioService.getArtifactMetaDataByGlobalId(artifactMetadata.getGlobalId());
                LOGGER.info("Checking that created schema is equal to the get schema");
                assertThat(metadata.getName(), is(artifactName));
                return true;
            });
    }

    public void createArtifactViaConfluentClient(Schema schema, String artifactName) throws IOException, RestClientException {
        int idOfSchema = confluentService.register(artifactName, schema);
        Schema newSchema = confluentService.getBySubjectAndId(artifactName, idOfSchema);
        LOGGER.info("Checking that created schema is equal to the get schema");
        assertThat(schema.toString(), is(newSchema.toString()));
        assertThat(confluentService.getVersion(artifactName, schema), is(confluentService.getVersion(artifactName, newSchema)));
    }

    public void updateArtifactViaConfluentClient(Schema schema, String artifactName) throws IOException, RestClientException {
        int idOfSchema = confluentService.register(artifactName, schema);
        Schema newSchema = confluentService.getBySubjectAndId(artifactName, idOfSchema);
        LOGGER.info("Checking that created schema is equal to the get schema");
        assertThat(schema.toString(), is(newSchema.toString()));
        assertThat(confluentService.getVersion(artifactName, schema), is(confluentService.getVersion(artifactName, newSchema)));
    }
}
