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

import io.apicurio.registry.types.ArtifactType;
import io.apicurio.tests.ApicurioRegistryBaseIT;
import io.apicurio.registry.rest.client.models.ArtifactContent;
import io.apicurio.registry.rest.client.models.Rule;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.apicurio.tests.utils.Constants.SMOKE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Tag(SMOKE)
@QuarkusIntegrationTest
class AllArtifactTypesIT extends ApicurioRegistryBaseIT {

    void doTest(String v1Resource, String v2Resource, String atype) throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();
        // Load/Assert resources exist.
        String v1Content = resourceToString("artifactTypes/" + v1Resource);
        String v2Content = resourceToString("artifactTypes/" + v2Resource);

        // Enable syntax validation global rule
        Rule rule = new Rule();
        rule.setType(RuleType.VALIDITY);
        rule.setConfig("SYNTAX_ONLY");
        registryClient.admin().rules().post(rule).get(3, TimeUnit.SECONDS);

        // Make sure we have rule
        retryOp((rc) -> rc.admin().rules().byRule(rule.getType().name()).get().get(3, TimeUnit.SECONDS));

        // Create artifact
        createArtifact(groupId, artifactId, atype, IoUtil.toStream(v1Content));

        // Test update (valid content)
        retryOp((rc) -> rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).test().put(IoUtil.toStream(v2Content)).get(3, TimeUnit.SECONDS));

        // Test update (invalid content)
        retryAssertClientError("RuleViolationException", 409, (rc) -> rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).test().put(IoUtil.toStream("{\"This is not a valid content.")).get(3, TimeUnit.SECONDS), errorCodeExtractor);

        // Update artifact (valid v2 content)
        createArtifactVersion(groupId, artifactId, IoUtil.toStream(v2Content));

        // Find artifact by content
        ArtifactContent content = new ArtifactContent();
        content.setContent(v1Content);
        VersionMetaData byContent = registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).meta().post(content, config -> {
            config.queryParameters.canonical = false;
        }).get(3, TimeUnit.SECONDS);
        assertNotNull(byContent);
        assertNotNull(byContent.getGlobalId());
        assertEquals(artifactId, byContent.getId());
        assertNotNull(byContent.getVersion());

        // Update artifact (invalid content)
        content.setContent("{\"This is not a valid content.");
        TestUtils.assertClientError("RuleViolationException", 409, () -> registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().post(content).get(3, TimeUnit.SECONDS), errorCodeExtractor);

        // Override Validation rule for the artifact
        rule.setConfig("NONE");
        registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).rules().post(rule).get(3, TimeUnit.SECONDS);

        // Make sure we have rule
        retryOp((rc) -> rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).rules().byRule(rule.getType().name()).get().get(3, TimeUnit.SECONDS));

        // Update artifact (invalid content) - should work now
        VersionMetaData amd2 = createArtifactVersion(groupId, artifactId, IoUtil.toStream("{\"This is not a valid content."));
        // Make sure artifact is fully registered
        retryOp((rc) -> rc.ids().globalIds().byGlobalId(amd2.getGlobalId()).get().get(3, TimeUnit.SECONDS));
    }

    @Test
    @Tag(ACCEPTANCE)
    void testAvro() throws Exception {
        doTest("avro/multi-field_v1.json", "avro/multi-field_v2.json", ArtifactType.AVRO);
    }

    @Test
    @Tag(ACCEPTANCE)
    void testProtobuf() throws Exception {
        doTest("protobuf/tutorial_v1.proto", "protobuf/tutorial_v2.proto", ArtifactType.PROTOBUF);
    }

    @Test
    @Tag(ACCEPTANCE)
    void testJsonSchema() throws Exception {
        doTest("jsonSchema/person_v1.json", "jsonSchema/person_v2.json", ArtifactType.JSON);
    }

    @Test
    void testKafkaConnect() throws Exception {
        doTest("kafkaConnect/simple_v1.json", "kafkaConnect/simple_v2.json", ArtifactType.KCONNECT);
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

    @AfterEach
    void deleteRules() throws Exception {
        registryClient.admin().rules().delete().get(3, TimeUnit.SECONDS);
        retryOp((rc) -> {
            List<RuleType> rules = rc.admin().rules().get().get(3, TimeUnit.SECONDS);
            assertEquals(0, rules.size(), "All global rules not deleted");
        });
    }
}
