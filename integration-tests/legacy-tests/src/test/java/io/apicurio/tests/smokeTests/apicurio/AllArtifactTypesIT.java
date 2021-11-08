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
package io.apicurio.tests.smokeTests.apicurio;

import io.apicurio.registry.client.RegistryRestClient;
import io.apicurio.registry.rest.beans.ArtifactMetaData;
import io.apicurio.registry.rest.beans.Rule;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.BaseIT;
import io.apicurio.tests.utils.subUtils.ArtifactUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import static io.apicurio.tests.common.Constants.SMOKE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Tag(SMOKE)
class AllArtifactTypesIT extends BaseIT {

    void doTest(RegistryRestClient client, String v1Resource, String v2Resource, ArtifactType atype) {
        String artifactId = TestUtils.generateArtifactId();
        try {
            // Load/Assert resources exist.
            String v1Content = resourceToString("artifactTypes/" + v1Resource);
            String v2Content = resourceToString("artifactTypes/" + v2Resource);

            // Enable syntax validation global rule
            Rule rule = new Rule();
            rule.setType(RuleType.VALIDITY);
            rule.setConfig("SYNTAX_ONLY");
            client.createGlobalRule(rule);

            // Make sure we have rule
            TestUtils.retry(() -> client.getGlobalRuleConfig(rule.getType()));

            // Create artifact
            ArtifactMetaData amd = ArtifactUtils.createArtifact(client, atype, artifactId, IoUtil.toStream(v1Content));
            // Make sure artifact is fully registered
            TestUtils.retry(() -> client.getArtifactMetaDataByGlobalId(amd.getGlobalId()));

            // Test update (valid content)
            client.testUpdateArtifact(artifactId, atype, IoUtil.toStream(v2Content));

            // Test update (invalid content)
            //WARNING, we are not 100% compliant  with the old API, before this and many other errors were a 400 error code, now they are 409
            //example https://github.com/Apicurio/apicurio-registry/blob/1.3.2.Final/tests/src/test/java/io/apicurio/tests/smokeTests/apicurio/AllArtifactTypesIT.java
            assertWebError(409, () -> client.testUpdateArtifact(artifactId, atype, IoUtil.toStream("{\"This is not a valid content.")));

            // Update artifact (valid v2 content)
            ArtifactUtils.updateArtifact(client, atype, artifactId, IoUtil.toStream(v2Content));

            // Find artifact by content
            ArtifactMetaData byContent = client.getArtifactMetaDataByContent(artifactId, IoUtil.toStream(v1Content));
            assertNotNull(byContent);
            assertNotNull(byContent.getGlobalId());
            assertEquals(artifactId, byContent.getId());
            assertNotNull(byContent.getVersion());

            // Update artifact (invalid content)
            assertWebError(409, () -> ArtifactUtils.updateArtifact(client, atype, artifactId, IoUtil.toStream("{\"This is not a valid content.")));

            // Override Validation rule for the artifact
            rule.setConfig("NONE");
            client.createArtifactRule(artifactId, rule);

            // Make sure we have rule
            TestUtils.retry(() -> client.getArtifactRuleConfig(artifactId, rule.getType()));

            // Update artifact (invalid content) - should work now
            ArtifactMetaData amd2 = ArtifactUtils.updateArtifact(client, atype, artifactId, IoUtil.toStream("{\"This is not a valid content."));
            // Make sure artifact is fully registered
            TestUtils.retry(() -> client.getArtifactMetaDataByGlobalId(amd2.getGlobalId()));
        } catch (Exception e) {
            LOGGER.error("Error on AllArtifactTypesIT", e);
            throw new IllegalStateException(e);
        }
    }

    @Test
    @Tag(ACCEPTANCE)
    void testAvro() {
        doTest(registryClient, "avro/multi-field_v1.json", "avro/multi-field_v2.json", ArtifactType.AVRO);
    }

    @Test
    @Tag(ACCEPTANCE)
    void testProtobuf() {
        doTest(registryClient, "protobuf/tutorial_v1.proto", "protobuf/tutorial_v2.proto", ArtifactType.PROTOBUF);
    }

    @Test
    @Tag(ACCEPTANCE)
    void testJsonSchema() {
        doTest(registryClient, "jsonSchema/person_v1.json", "jsonSchema/person_v2.json", ArtifactType.JSON);
    }

    @Test
    void testKafkaConnect() {
        doTest(registryClient, "kafkaConnect/simple_v1.json", "kafkaConnect/simple_v2.json", ArtifactType.KCONNECT);
    }

    @Test
    void testOpenApi30() {
        doTest(registryClient, "openapi/3.0-petstore_v1.json", "openapi/3.0-petstore_v2.json", ArtifactType.OPENAPI);
    }

    @Test
    void testAsyncApi() {
        doTest(registryClient, "asyncapi/2.0-streetlights_v1.json", "asyncapi/2.0-streetlights_v2.json", ArtifactType.ASYNCAPI);
    }

    @Test
    void testGraphQL() {
        doTest(registryClient, "graphql/swars_v1.graphql", "graphql/swars_v2.graphql", ArtifactType.GRAPHQL);
    }

    @AfterEach
    void deleteRules() throws Exception {
        registryClient.deleteAllGlobalRules();
        TestUtils.retry(() -> {
            List<RuleType> rules = registryClient.listGlobalRules();
            assertEquals(0, rules.size(), "All global rules not deleted");
        });
    }
}
