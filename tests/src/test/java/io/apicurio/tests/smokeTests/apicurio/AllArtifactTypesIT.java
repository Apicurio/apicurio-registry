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
package io.apicurio.tests.smokeTests.apicurio;

import static io.apicurio.tests.Constants.SMOKE;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.apicurio.registry.types.ArtifactType;
import io.apicurio.tests.BaseIT;

@Tag(SMOKE)
class AllArtifactTypesIT extends BaseIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(AllArtifactTypesIT.class);
    
    void doTest(String v1Resource, String v2Resource, ArtifactType atype) {
        // Load/Assert resources exist.
        String v1Content = resourceToString("artifactTypes/" + v1Resource);
        String v2Content = resourceToString("artifactTypes/" + v2Resource);
        
        // Enable syntax validation global rule
        
        // Create artifact
        
        // Update artifact (same content)
        
        // Find artifact by content
        
        // Test update (valid content)
        
        // Test update (invalid content)
        
        // Update artifact (invalid content)
        
        // Override Validation rule for the artifact
        
        // Update artifact (invalid content)
        
    }

    @Test
    void testAvro() {
        doTest("avro/multi-field_v1.json", "avro/multi-field_v2.json", ArtifactType.AVRO);
    }

    @Test
    void testProtobuf() {
        doTest("protobuf/tutorial_v1.proto", "protobuf/tutorial_v2.proto", ArtifactType.PROTOBUF);
    }

    @Test
    void testJsonSchema() {
        doTest("jsonSchema/person_v1.json", "jsonSchema/person_v2.json", ArtifactType.JSON);
    }

    @Test
    void testKafkaConnect() {
        doTest("kafkaConnect/simple_v1.json", "kafkaConnect/simple_v2.json", ArtifactType.KCONNECT);
    }

    @Test
    void testOpenApi() {
        doTest("openapi/2.0-petstore_v1.json", "openapi/2.0-petstore_v2.json", ArtifactType.OPENAPI);
        doTest("openapi/3.0-petstore_v1.json", "openapi/3.0-petstore_v2.json", ArtifactType.OPENAPI);
    }

    @Test
    void testAsyncApi() {
        doTest("asyncapi/2.0-streetlights_v1.json", "asyncapi/2.0-streetlights_v2.json", ArtifactType.ASYNCAPI);
    }

    @Test
    void testGraphQL() {
        doTest("graphql/swars_v1.graphql", "graphql/swars_v2.graphql", ArtifactType.GRAPHQL);
    }

    @AfterEach
    void deleteRules() {
        apicurioService.deleteAllGlobalRules();
    }
}

