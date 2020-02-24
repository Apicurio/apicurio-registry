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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import javax.ws.rs.WebApplicationException;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.apicurio.registry.rest.beans.ArtifactMetaData;
import io.apicurio.registry.rest.beans.Rule;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.tests.BaseIT;
import io.apicurio.tests.utils.subUtils.ArtifactUtils;

@Tag(SMOKE)
class AllArtifactTypesIT extends BaseIT {
    
    void doTest(String v1Resource, String v2Resource, ArtifactType atype) throws Exception {
        String artifactId = getClass().getSimpleName() + "-" + atype.name();
        try {
            // Load/Assert resources exist.
            String v1Content = resourceToString("artifactTypes/" + v1Resource);
            String v2Content = resourceToString("artifactTypes/" + v2Resource);
            
            // Enable syntax validation global rule
            Rule rule = new Rule();
            rule.setType(RuleType.VALIDITY);
            rule.setConfig("SYNTAX_ONLY");
            apicurioService.createGlobalRule(rule);
            
            // Create artifact
            ArtifactUtils.createArtifact(apicurioService, atype, artifactId, IoUtil.toStream(v1Content));
            
            // Test update (valid content)
            apicurioService.testUpdateArtifact(artifactId, atype, IoUtil.toStream(v2Content));
            
            // Test update (invalid content)
            try {
                apicurioService.testUpdateArtifact(artifactId, atype, IoUtil.toStream("This is not valid content"));
                fail("Expected an exception for 'testUpdateArtifact' for invalid content.");
            } catch (WebApplicationException e) {
                assertEquals(400, e.getResponse().getStatus());
            }
    
            // Update artifact (valid v2 content)
            ArtifactUtils.updateArtifact(apicurioService, atype, artifactId, IoUtil.toStream(v2Content));
            
            // Find artifact by content
            ArtifactMetaData byContent = apicurioService.getArtifactMetaDataByContent(artifactId, IoUtil.toStream(v1Content));
            assertNotNull(byContent);
            assertNotNull(byContent.getGlobalId());
            assertEquals(artifactId, byContent.getId());
            assertNotNull(byContent.getVersion());
    
            // Update artifact (invalid content)
            try {
                ArtifactUtils.updateArtifact(apicurioService, atype, artifactId, IoUtil.toStream("This is not valid content."));
                fail("Expected a 400 error");
            } catch (WebApplicationException e) {
                assertEquals(400, e.getResponse().getStatus());
            }
            
            // Override Validation rule for the artifact
            rule.setConfig("NONE");
            apicurioService.createArtifactRule(artifactId, rule);
            
            // Update artifact (invalid content) - should work now
            ArtifactUtils.updateArtifact(apicurioService, atype, artifactId, IoUtil.toStream("This is not valid content."));
        } finally {
            apicurioService.deleteAllGlobalRules();
            try { apicurioService.deleteArtifact(artifactId); } catch (Exception e) {}
        }
    }

    @Test
    void testAvro() throws Exception {
        doTest("avro/multi-field_v1.json", "avro/multi-field_v2.json", ArtifactType.AVRO);
    }

    @Test
    void testProtobuf() throws Exception {
        doTest("protobuf/tutorial_v1.proto", "protobuf/tutorial_v2.proto", ArtifactType.PROTOBUF);
    }

    @Test
    void testJsonSchema() throws Exception {
        doTest("jsonSchema/person_v1.json", "jsonSchema/person_v2.json", ArtifactType.JSON);
    }

    @Test
    void testKafkaConnect() throws Exception {
        doTest("kafkaConnect/simple_v1.json", "kafkaConnect/simple_v2.json", ArtifactType.KCONNECT);
    }

    @Test
    void testOpenApi20() throws Exception {
        doTest("openapi/2.0-petstore_v1.json", "openapi/2.0-petstore_v2.json", ArtifactType.OPENAPI);
    }

    @Test
    void testOpenApi30() throws Exception {
        doTest("openapi/3.0-petstore_v1.json", "openapi/3.0-petstore_v2.json", ArtifactType.OPENAPI);
    }

    @Test
    void testAsyncApi() throws Exception {
        doTest("asyncapi/2.0-streetlights_v1.json", "asyncapi/2.0-streetlights_v2.json", ArtifactType.ASYNCAPI);
    }

    @Test
    void testGraphQL() throws Exception {
        doTest("graphql/swars_v1.graphql", "graphql/swars_v2.graphql", ArtifactType.GRAPHQL);
    }

}
