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
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.ConcurrentUtil;
import io.apicurio.tests.interfaces.TestSeparator;
import io.apicurio.tests.utils.subUtils.TestUtils;
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.restassured.RestAssured;
import io.restassured.parsing.Parser;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.CompletionStage;

public abstract class BaseIT implements TestSeparator, Constants {

    private static final Logger LOGGER = LoggerFactory.getLogger(BaseIT.class);
    private static KafkaFacade kafkaCluster = new KafkaFacade();
    private static RegistryFacade registries = new RegistryFacade();

    protected static SchemaRegistryClient confluentService;
    protected static RegistryService apicurioService;

    @BeforeAll
    static void beforeAll() throws Exception {
        kafkaCluster.start();

        registries.start();
        TestUtils.waitFor("Cannot connect to registries on " + RegistryFacade.REGISTRY_URL + ":" + RegistryFacade.REGISTRY_PORT + " in timeout!",
                Constants.POLL_INTERVAL, Constants.TIMEOUT_FOR_REGISTRY_START_UP, RegistryFacade::isReachable);
        RestAssured.baseURI = "http://" + RegistryFacade.REGISTRY_URL + ":" + RegistryFacade.REGISTRY_PORT;
        LOGGER.info("Registry app is running on {}:{}", RegistryFacade.REGISTRY_URL, RegistryFacade.REGISTRY_PORT);
        RestAssured.defaultParser = Parser.JSON;
        confluentService = new CachedSchemaRegistryClient("http://" + RegistryFacade.REGISTRY_URL + ":" + RegistryFacade.REGISTRY_PORT + "/confluent", 3);
        apicurioService = RegistryClient.create("http://"  + RegistryFacade.REGISTRY_URL + ":" + RegistryFacade.REGISTRY_PORT);
    }

    @AfterAll
    static void afterAll(TestInfo info) {
        kafkaCluster.stop();
        registries.stop();
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

        TestUtils.writeFile(logDir + "/registries-stdout.log", registries.getRegistryStdOut());
        TestUtils.writeFile(logDir + "/registries-stderr.log", registries.getRegistryStdErr());
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
}
