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

import io.apicurio.registry.client.RegistryService;
import io.apicurio.registry.rest.beans.ArtifactMetaData;
import io.apicurio.registry.rest.beans.EditableMetaData;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.ConcurrentUtil;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.interfaces.TestSeparator;
import io.restassured.RestAssured;
import io.restassured.parsing.Parser;
import org.apache.avro.Schema;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

public abstract class BaseIT implements TestSeparator, Constants {

    protected static final Logger LOGGER = LoggerFactory.getLogger(BaseIT.class);
    protected static KafkaFacade kafkaCluster = new KafkaFacade();
    private static RegistryFacade registry = new RegistryFacade();

    protected final String resourceToString(String resourceName) {
        try (InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourceName)) {
            Assertions.assertNotNull(stream, "Resource not found: " + resourceName);
            return new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeAll
    static void beforeAll() throws Exception {
        if (!TestUtils.isExternalRegistry()) {
            registry.start();
        } else {
            LOGGER.info("Going to use already running registries on {}", TestUtils.getRegistryUrl());
        }
        TestUtils.waitFor("Cannot connect to registries on " + TestUtils.getRegistryUrl() + " in timeout!",
                          Constants.POLL_INTERVAL, Constants.TIMEOUT_FOR_REGISTRY_START_UP, TestUtils::isReachable);
        TestUtils.waitFor("Registry reports is ready",
                Constants.POLL_INTERVAL, Constants.TIMEOUT_FOR_REGISTRY_READY, () -> TestUtils.isReady(false), () -> TestUtils.isReady(true));
        RestAssured.baseURI = TestUtils.getRegistryUrl();
        LOGGER.info("Registry app is running on {}", RestAssured.baseURI);
        RestAssured.defaultParser = Parser.JSON;
    }

    @AfterAll
    static void afterAll(TestInfo info) throws Exception {
        if (!TestUtils.isExternalRegistry()) {
            registry.stop();
            Thread.sleep(3000);
            //noinspection OptionalGetWithoutIsPresent
            storeRegistryLog(info.getTestClass().get().getCanonicalName());
        }
    }

    private static void storeRegistryLog(String className) {
        final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm");
        simpleDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        String currentDate = simpleDateFormat.format(Calendar.getInstance().getTime());
        File logDir = new File("target/logs/" + className + "-" + currentDate);

        if (!logDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            logDir.mkdirs();
        }

        TestUtils.writeFile(logDir + "/registries-stdout.log", registry.getRegistryStdOut());
        TestUtils.writeFile(logDir + "/registries-stderr.log", registry.getRegistryStdErr());
    }

    protected Map<String, String> createMultipleArtifacts(RegistryService apicurioService, int count) throws Exception {
        Map<String, String> idMap = new HashMap<>();

        for (int x = 0; x < count; x++) {
            String name = "myrecord" + x;
            String artifactId = TestUtils.generateArtifactId();

            String artifactDefinition = "{\"type\":\"record\",\"name\":\"" + name + "\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";
            ByteArrayInputStream artifactData = new ByteArrayInputStream(artifactDefinition.getBytes(StandardCharsets.UTF_8));
            CompletionStage<ArtifactMetaData> csResult = apicurioService.createArtifact(ArtifactType.AVRO, artifactId, null, artifactData);

            // Make sure artifact is fully registered
            ArtifactMetaData amd = ConcurrentUtil.result(csResult);
            TestUtils.retry(() -> apicurioService.getArtifactMetaDataByGlobalId(amd.getGlobalId()));

            LOGGER.info("Created record with name: {} and ID: {}", amd.getName(), amd.getId());
            idMap.put(name, amd.getId());
        }

        return idMap;
    }

    protected void deleteMultipleArtifacts(RegistryService apicurioService, Map<String, String> idMap) {
        for (Map.Entry<String, String> entry : idMap.entrySet()) {
            apicurioService.deleteArtifact(entry.getValue());
            LOGGER.info("Deleted artifact {} with ID: {}", entry.getKey(), entry.getValue());
        }
    }

    public void createArtifactViaApicurioClient(RegistryService apicurioService, Schema schema, String artifactName) throws TimeoutException {
        CompletionStage<ArtifactMetaData> csa = apicurioService.createArtifact(
                ArtifactType.AVRO,
                artifactName,
                null,
                new ByteArrayInputStream(schema.toString().getBytes(StandardCharsets.UTF_8))
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

    public void updateArtifactViaApicurioClient(RegistryService apicurioService, Schema schema, String artifactName) throws TimeoutException {
        CompletionStage<ArtifactMetaData> csa = apicurioService.updateArtifact(
                artifactName,
                ArtifactType.AVRO,
                new ByteArrayInputStream(schema.toString().getBytes(StandardCharsets.UTF_8))
        );
        ArtifactMetaData artifactMetadata = ConcurrentUtil.result(csa);
        EditableMetaData editableMetaData = new EditableMetaData();
        editableMetaData.setName(artifactName);
        apicurioService.updateArtifactMetaData(artifactName, editableMetaData);
        apicurioService.reset(); // clear cache
        // wait for global id store to populate (in case of Kafka / Streams)
        TestUtils.waitFor("Wait until artifact globalID mapping is finished", Constants.POLL_INTERVAL, Constants.TIMEOUT_GLOBAL,
            () -> {
                ArtifactMetaData metadata = apicurioService.getArtifactMetaDataByGlobalId(artifactMetadata.getGlobalId());
                LOGGER.info("Checking that created schema is equal to the get schema");
                assertThat(metadata.getName(), is(artifactName));
                return true;
            });
    }

    protected String generateArtifactId() {
        return TestUtils.generateArtifactId();
    }
}
