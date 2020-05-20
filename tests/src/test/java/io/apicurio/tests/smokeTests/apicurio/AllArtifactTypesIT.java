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

import io.apicurio.registry.client.RegistryService;
import io.apicurio.registry.rest.beans.ArtifactMetaData;
import io.apicurio.registry.rest.beans.Rule;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.tests.RegistryServiceTest;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.BaseIT;
import io.apicurio.tests.utils.subUtils.ArtifactUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;

import static io.apicurio.tests.Constants.SMOKE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Tag(SMOKE)
class AllArtifactTypesIT extends BaseIT {

    void doTest(RegistryService service, String v1Resource, String v2Resource, ArtifactType atype) {
        String artifactId = TestUtils.generateArtifactId();
        try {
            // Load/Assert resources exist.
            String v1Content = resourceToString("artifactTypes/" + v1Resource);
            String v2Content = resourceToString("artifactTypes/" + v2Resource);

            // Enable syntax validation global rule
            Rule rule = new Rule();
            rule.setType(RuleType.VALIDITY);
            rule.setConfig("SYNTAX_ONLY");
            service.createGlobalRule(rule);

            // Make sure we have rule
            TestUtils.retry(() -> service.getGlobalRuleConfig(rule.getType()));

            // Create artifact
            ArtifactMetaData amd = ArtifactUtils.createArtifact(service, atype, artifactId, IoUtil.toStream(v1Content));
            // Make sure artifact is fully registered
            TestUtils.retry(() -> service.getArtifactMetaDataByGlobalId(amd.getGlobalId()));

            // Test update (valid content)
            service.testUpdateArtifact(artifactId, atype, IoUtil.toStream(v2Content));

            // Test update (invalid content)
            TestUtils.assertWebError(400, () -> service.testUpdateArtifact(artifactId, atype, IoUtil.toStream("This is not valid content")));

            // Update artifact (valid v2 content)
            ArtifactUtils.updateArtifact(service, atype, artifactId, IoUtil.toStream(v2Content));

            // Find artifact by content
            ArtifactMetaData byContent = service.getArtifactMetaDataByContent(artifactId, IoUtil.toStream(v1Content));
            assertNotNull(byContent);
            assertNotNull(byContent.getGlobalId());
            assertEquals(artifactId, byContent.getId());
            assertNotNull(byContent.getVersion());

            // Update artifact (invalid content)
            TestUtils.assertWebError(400, () -> ArtifactUtils.updateArtifact(service, atype, artifactId, IoUtil.toStream("This is not valid content.")));

            // Override Validation rule for the artifact
            rule.setConfig("NONE");
            service.createArtifactRule(artifactId, rule);

            // Make sure we have rule
            TestUtils.retry(() -> service.getArtifactRuleConfig(rule.getType(), artifactId));

            // Update artifact (invalid content) - should work now
            ArtifactMetaData amd2 = ArtifactUtils.updateArtifact(service, atype, artifactId, IoUtil.toStream("This is not valid content."));
            // Make sure artifact is fully registered
            TestUtils.retry(() -> service.getArtifactMetaDataByGlobalId(amd2.getGlobalId()));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        } finally {
            service.deleteAllGlobalRules();
        }
    }

    @RegistryServiceTest(localOnly = false)
    void testAvro(RegistryService service) {
        doTest(service, "avro/multi-field_v1.json", "avro/multi-field_v2.json", ArtifactType.AVRO);
    }

    @RegistryServiceTest(localOnly = false)
    void testProtobuf(RegistryService service) {
        doTest(service, "protobuf/tutorial_v1.proto", "protobuf/tutorial_v2.proto", ArtifactType.PROTOBUF);
    }

    @RegistryServiceTest(localOnly = false)
    void testJsonSchema(RegistryService service) {
        doTest(service, "jsonSchema/person_v1.json", "jsonSchema/person_v2.json", ArtifactType.JSON);
    }

    @RegistryServiceTest(localOnly = false)
    void testKafkaConnect(RegistryService service) {
        doTest(service, "kafkaConnect/simple_v1.json", "kafkaConnect/simple_v2.json", ArtifactType.KCONNECT);
    }

    @RegistryServiceTest(localOnly = false)
    void testOpenApi30(RegistryService service) {
        doTest(service, "openapi/3.0-petstore_v1.json", "openapi/3.0-petstore_v2.json", ArtifactType.OPENAPI);
    }

    @RegistryServiceTest(localOnly = false)
    void testAsyncApi(RegistryService service) {
        doTest(service, "asyncapi/2.0-streetlights_v1.json", "asyncapi/2.0-streetlights_v2.json", ArtifactType.ASYNCAPI);
    }

    @RegistryServiceTest(localOnly = false)
    void testGraphQL(RegistryService service) {
        doTest(service, "graphql/swars_v1.graphql", "graphql/swars_v2.graphql", ArtifactType.GRAPHQL);
    }

    @AfterEach
    void deleteRules(RegistryService service) {
        service.deleteAllGlobalRules();
        service.listArtifacts().forEach(artifactId -> {
            service.deleteArtifact(artifactId);
        });
    }
}
