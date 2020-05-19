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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.apicurio.tests.Constants.SMOKE;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Tag(SMOKE)
class RulesResourceIT extends BaseIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(RulesResourceIT.class);

    @RegistryServiceTest(localOnly = false)
    void createAndDeleteGlobalRules(RegistryService service) throws Exception {
        // Create a global rule
        Rule rule = new Rule();
        rule.setType(RuleType.VALIDITY);
        rule.setConfig("SYNTAX_ONLY");

        TestUtils.retry(() -> service.createGlobalRule(rule));

        // Check the rule was created.
        TestUtils.retry(() -> {
            Rule ruleConfig = service.getGlobalRuleConfig(RuleType.VALIDITY);
            assertNotNull(ruleConfig);
            assertEquals("SYNTAX_ONLY", ruleConfig.getConfig());
        });

        // Delete all rules
        service.deleteAllGlobalRules();

        // No rules listed now
        TestUtils.retry(() -> {
            List<RuleType> rules = service.listGlobalRules();
            assertEquals(0, rules.size());
        });

        // Should be null/error (never configured the COMPATIBILITY rule)
        TestUtils.assertWebError(404, () -> service.getGlobalRuleConfig(RuleType.COMPATIBILITY));

        // Should be null/error (deleted the VALIDITY rule)
        TestUtils.assertWebError(404, () -> service.getGlobalRuleConfig(RuleType.VALIDITY));
    }

    @RegistryServiceTest(localOnly = false)
    void createAndValidateGlobalRules(RegistryService service) throws Exception {
        Rule rule = new Rule();
        rule.setType(RuleType.VALIDITY);
        rule.setConfig("SYNTAX_ONLY");

        TestUtils.retry(() -> service.createGlobalRule(rule));
        LOGGER.info("Created rule: {} - {}", rule.getType(), rule.getConfig());

        TestUtils.assertWebError(409, () -> service.createGlobalRule(rule), true);

        String invalidArtifactDefinition = "<type>record</type>\n<name>test</name>";
        String artifactId = TestUtils.generateArtifactId();

        LOGGER.info("Invalid artifact sent {}", invalidArtifactDefinition);
        TestUtils.assertWebError(400, () -> ArtifactUtils.createArtifact(service, ArtifactType.AVRO, artifactId, IoUtil.toStream(invalidArtifactDefinition)));
        TestUtils.assertWebError(404, () -> ArtifactUtils.updateArtifact(service, ArtifactType.AVRO, artifactId, IoUtil.toStream(invalidArtifactDefinition)));

        ByteArrayInputStream artifactData = new ByteArrayInputStream("{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"long\"}]}".getBytes(StandardCharsets.UTF_8));

        ArtifactMetaData metaData = ArtifactUtils.createArtifact(service, ArtifactType.AVRO, artifactId, artifactData);
        LOGGER.info("Created artifact {} with metadata {}", artifactId, metaData.toString());

        artifactData = new ByteArrayInputStream("{\"type\":\"record\",\"name\":\"myrecord2\",\"fields\":[{\"name\":\"bar\",\"type\":\"long\"}]}".getBytes(StandardCharsets.UTF_8));
        metaData = ArtifactUtils.updateArtifact(service, ArtifactType.AVRO, artifactId, artifactData);
        LOGGER.info("Artifact with Id:{} was updated:{}", artifactId, metaData.toString());

        TestUtils.retry(() -> {
            List<Long> artifactVersions = service.listArtifactVersions(artifactId);
            LOGGER.info("Available versions of artifact with ID {} are: {}", artifactId, artifactVersions.toString());
            assertThat(artifactVersions, hasItems(1L, 2L));
        });
    }

    @RegistryServiceTest(localOnly = false)
    void createAndValidateArtifactRule(RegistryService service) throws Exception {
        String artifactId1 = TestUtils.generateArtifactId();
        String artifactDefinition = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";

        ByteArrayInputStream artifactData = new ByteArrayInputStream(artifactDefinition.getBytes(StandardCharsets.UTF_8));
        ArtifactMetaData metaData = ArtifactUtils.createArtifact(service, ArtifactType.AVRO, artifactId1, artifactData);
        LOGGER.info("Created artifact {} with metadata {}", artifactId1, metaData);
        ArtifactMetaData amd1 = metaData;
        TestUtils.retry(() -> service.getArtifactMetaDataByGlobalId(amd1.getGlobalId()));

        String artifactId2 = TestUtils.generateArtifactId();
        artifactDefinition = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";
        artifactData = new ByteArrayInputStream(artifactDefinition.getBytes(StandardCharsets.UTF_8));

        metaData = ArtifactUtils.createArtifact(service, ArtifactType.AVRO, artifactId2, artifactData);
        LOGGER.info("Created artifact {} with metadata {}", artifactId2, metaData);
        ArtifactMetaData amd2 = metaData;
        TestUtils.retry(() -> service.getArtifactMetaDataByGlobalId(amd2.getGlobalId()));

        Rule rule = new Rule();
        rule.setType(RuleType.VALIDITY);
        rule.setConfig("SYNTAX_ONLY");

        service.createArtifactRule(artifactId1, rule);
        LOGGER.info("Created rule: {} - {} for artifact {}", rule.getType(), rule.getConfig(), artifactId1);

        TestUtils.assertWebError(409, () -> service.createArtifactRule(artifactId1, rule), true);

        String invalidArtifactDefinition = "<type>record</type>\n<name>test</name>";
        artifactData = new ByteArrayInputStream(invalidArtifactDefinition.getBytes(StandardCharsets.UTF_8));

        ByteArrayInputStream iad = artifactData;
        TestUtils.assertWebError(400, () -> ArtifactUtils.updateArtifact(service, ArtifactType.AVRO, artifactId1, iad));

        String updatedArtifactData = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"bar\",\"type\":\"long\"}]}";

        artifactData = new ByteArrayInputStream(updatedArtifactData.getBytes(StandardCharsets.UTF_8));
        metaData = ArtifactUtils.updateArtifact(service, ArtifactType.AVRO, artifactId2, artifactData);
        LOGGER.info("Artifact with ID {} was updated: {}", artifactId2, metaData.toString());

        artifactData = new ByteArrayInputStream(updatedArtifactData.getBytes(StandardCharsets.UTF_8));
        metaData = ArtifactUtils.updateArtifact(service, ArtifactType.AVRO, artifactId1, artifactData);
        LOGGER.info("Artifact with ID {} was updated: {}", artifactId1, metaData.toString());

        TestUtils.retry(() -> {
            List<Long> artifactVersions = service.listArtifactVersions(artifactId1);
            LOGGER.info("Available versions of artifact with ID {} are: {}", artifactId1, artifactVersions.toString());
            assertThat(artifactVersions, hasItems(1L, 2L));

            artifactVersions = service.listArtifactVersions(artifactId2);
            LOGGER.info("Available versions of artifact with ID {} are: {}", artifactId2, artifactVersions.toString());
            assertThat(artifactVersions, hasItems(1L, 2L));
        });
    }

    @RegistryServiceTest(localOnly = false)
    void testRulesDeletedWithArtifact(RegistryService service) throws Exception {
        String artifactId1 = TestUtils.generateArtifactId();
        String artifactDefinition = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";

        ByteArrayInputStream artifactData = new ByteArrayInputStream(artifactDefinition.getBytes(StandardCharsets.UTF_8));
        ArtifactMetaData metaData = ArtifactUtils.createArtifact(service, ArtifactType.AVRO, artifactId1, artifactData);
        LOGGER.info("Created artifact {} with metadata {}", artifactId1, metaData);
        ArtifactMetaData amd1 = metaData;
        TestUtils.retry(() -> service.getArtifactMetaDataByGlobalId(amd1.getGlobalId()));

        Rule rule = new Rule();
        rule.setType(RuleType.VALIDITY);
        rule.setConfig("SYNTAX_ONLY");

        service.createArtifactRule(artifactId1, rule);
        LOGGER.info("Created rule: {} - {} for artifact {}", rule.getType(), rule.getConfig(), artifactId1);

        service.deleteArtifact(artifactId1);

        assertThat(0, is(service.listArtifacts().size()));

        TestUtils.assertWebError(404, () -> service.listArtifactRules(artifactId1));
        TestUtils.assertWebError(404, () -> service.getArtifactRuleConfig(RuleType.VALIDITY, artifactId1));
    }

    @AfterEach
    void clearRules(RegistryService service) {
        service.deleteAllGlobalRules();
        service.listArtifacts().forEach(artifactId -> {
            service.deleteArtifact(artifactId);
        });
    }
}
