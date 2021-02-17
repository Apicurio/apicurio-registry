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

import io.apicurio.registry.client.exception.ArtifactNotFoundException;
import io.apicurio.registry.client.exception.RestClientException;
import io.apicurio.registry.client.exception.RuleAlreadyExistsException;
import io.apicurio.registry.client.exception.RuleNotFoundException;
import io.apicurio.registry.client.exception.RuleViolationException;
import io.apicurio.registry.rest.v1.beans.ArtifactMetaData;
import io.apicurio.registry.rest.v1.beans.Rule;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.BaseIT;
import io.apicurio.tests.utils.subUtils.ArtifactUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Function;

import static io.apicurio.tests.common.Constants.SMOKE;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Tag(SMOKE)
class RulesResourceIT extends BaseIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(RulesResourceIT.class);

    private Function<Exception, Integer> errorCodeExtractor = e -> ((RestClientException) e).getError().getErrorCode();

    @Test
    void createAndDeleteGlobalRules() throws Exception {
        // Create a global rule
        Rule rule = new Rule();
        rule.setType(RuleType.VALIDITY);
        rule.setConfig("SYNTAX_ONLY");

        TestUtils.retry(() -> registryClient.createGlobalRule(rule));

        // Check the rule was created.
        TestUtils.retry(() -> {
            Rule ruleConfig = registryClient.getGlobalRuleConfig(RuleType.VALIDITY);
            assertNotNull(ruleConfig);
            assertEquals("SYNTAX_ONLY", ruleConfig.getConfig());
        });

        // Delete all rules
        registryClient.deleteAllGlobalRules();

        // No rules listed now
        TestUtils.retry(() -> {
            List<RuleType> rules = registryClient.listGlobalRules();
            assertEquals(0, rules.size());
        });

        // Should be null/error (never configured the COMPATIBILITY rule)
        TestUtils.assertClientError(RuleNotFoundException.class.getSimpleName(), 404, () -> registryClient.getGlobalRuleConfig(RuleType.COMPATIBILITY), errorCodeExtractor);

        // Should be null/error (deleted the VALIDITY rule)
        TestUtils.assertClientError(RuleNotFoundException.class.getSimpleName(), 404, () -> registryClient.getGlobalRuleConfig(RuleType.VALIDITY), errorCodeExtractor);
    }

    @Test
    @Tag(ACCEPTANCE)
    void createAndValidateGlobalRules() throws Exception {
        Rule rule = new Rule();
        rule.setType(RuleType.VALIDITY);
        rule.setConfig("SYNTAX_ONLY");

        TestUtils.retry(() -> registryClient.createGlobalRule(rule));
        LOGGER.info("Created rule: {} - {}", rule.getType(), rule.getConfig());

        TestUtils.assertClientError(RuleAlreadyExistsException.class.getSimpleName(), 409, () -> registryClient.createGlobalRule(rule), true, errorCodeExtractor);

        String invalidArtifactDefinition = "<type>record</type>\n<name>test</name>";
        String artifactId = TestUtils.generateArtifactId();

        LOGGER.info("Invalid artifact sent {}", invalidArtifactDefinition);
        TestUtils.assertClientError(RuleViolationException.class.getSimpleName(), 409, () -> ArtifactUtils.createArtifact(registryClient, ArtifactType.AVRO, artifactId, IoUtil.toStream(invalidArtifactDefinition)), errorCodeExtractor);
        TestUtils.assertClientError(ArtifactNotFoundException.class.getSimpleName(), 404, () -> ArtifactUtils.updateArtifact(registryClient, ArtifactType.AVRO, artifactId, IoUtil.toStream(invalidArtifactDefinition)), errorCodeExtractor);

        ByteArrayInputStream artifactData = new ByteArrayInputStream("{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"long\"}]}".getBytes(StandardCharsets.UTF_8));

        ArtifactMetaData metaData = ArtifactUtils.createArtifact(registryClient, ArtifactType.AVRO, artifactId, artifactData);
        LOGGER.info("Created artifact {} with metadata {}", artifactId, metaData.toString());

        artifactData = new ByteArrayInputStream("{\"type\":\"record\",\"name\":\"myrecord2\",\"fields\":[{\"name\":\"bar\",\"type\":\"long\"}]}".getBytes(StandardCharsets.UTF_8));
        metaData = ArtifactUtils.updateArtifact(registryClient, ArtifactType.AVRO, artifactId, artifactData);
        LOGGER.info("Artifact with Id:{} was updated:{}", artifactId, metaData.toString());

        TestUtils.retry(() -> {
            List<Long> artifactVersions = registryClient.listArtifactVersions(artifactId);
            LOGGER.info("Available versions of artifact with ID {} are: {}", artifactId, artifactVersions.toString());
            assertThat(artifactVersions, hasItems(1L, 2L));
        });
    }

    @Test
    @Tag(ACCEPTANCE)
    void createAndValidateArtifactRule() throws Exception {
        String artifactId1 = TestUtils.generateArtifactId();
        String artifactDefinition = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";

        ByteArrayInputStream artifactData = new ByteArrayInputStream(artifactDefinition.getBytes(StandardCharsets.UTF_8));
        ArtifactMetaData metaData = ArtifactUtils.createArtifact(registryClient, ArtifactType.AVRO, artifactId1, artifactData);
        LOGGER.info("Created artifact {} with metadata {}", artifactId1, metaData);
        ArtifactMetaData amd1 = metaData;
        TestUtils.retry(() -> registryClient.getArtifactMetaDataByGlobalId(amd1.getGlobalId()));

        String artifactId2 = TestUtils.generateArtifactId();
        artifactDefinition = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";
        artifactData = new ByteArrayInputStream(artifactDefinition.getBytes(StandardCharsets.UTF_8));

        metaData = ArtifactUtils.createArtifact(registryClient, ArtifactType.AVRO, artifactId2, artifactData);
        LOGGER.info("Created artifact {} with metadata {}", artifactId2, metaData);
        ArtifactMetaData amd2 = metaData;
        TestUtils.retry(() -> registryClient.getArtifactMetaDataByGlobalId(amd2.getGlobalId()));

        Rule rule = new Rule();
        rule.setType(RuleType.VALIDITY);
        rule.setConfig("SYNTAX_ONLY");

        registryClient.createArtifactRule(artifactId1, rule);
        LOGGER.info("Created rule: {} - {} for artifact {}", rule.getType(), rule.getConfig(), artifactId1);

        TestUtils.assertClientError(RuleAlreadyExistsException.class.getSimpleName(), 409, () -> registryClient.createArtifactRule(artifactId1, rule), true, errorCodeExtractor);

        String invalidArtifactDefinition = "<type>record</type>\n<name>test</name>";
        artifactData = new ByteArrayInputStream(invalidArtifactDefinition.getBytes(StandardCharsets.UTF_8));

        ByteArrayInputStream iad = artifactData;
        TestUtils.assertClientError(RuleViolationException.class.getSimpleName(), 409, () -> ArtifactUtils.updateArtifact(registryClient, ArtifactType.AVRO, artifactId1, iad), errorCodeExtractor);

        String updatedArtifactData = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"bar\",\"type\":\"long\"}]}";

        artifactData = new ByteArrayInputStream(updatedArtifactData.getBytes(StandardCharsets.UTF_8));
        metaData = ArtifactUtils.updateArtifact(registryClient, ArtifactType.AVRO, artifactId2, artifactData);
        LOGGER.info("Artifact with ID {} was updated: {}", artifactId2, metaData.toString());

        artifactData = new ByteArrayInputStream(updatedArtifactData.getBytes(StandardCharsets.UTF_8));
        metaData = ArtifactUtils.updateArtifact(registryClient, ArtifactType.AVRO, artifactId1, artifactData);
        LOGGER.info("Artifact with ID {} was updated: {}", artifactId1, metaData.toString());

        TestUtils.retry(() -> {
            List<Long> artifactVersions = registryClient.listArtifactVersions(artifactId1);
            LOGGER.info("Available versions of artifact with ID {} are: {}", artifactId1, artifactVersions.toString());
            assertThat(artifactVersions, hasItems(1L, 2L));

            artifactVersions = registryClient.listArtifactVersions(artifactId2);
            LOGGER.info("Available versions of artifact with ID {} are: {}", artifactId2, artifactVersions.toString());
            assertThat(artifactVersions, hasItems(1L, 2L));
        });
    }

    @Test
    @Tag(ACCEPTANCE)
    void testRulesDeletedWithArtifact() throws Exception {
        String artifactId1 = TestUtils.generateArtifactId();
        String artifactDefinition = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";

        ByteArrayInputStream artifactData = new ByteArrayInputStream(artifactDefinition.getBytes(StandardCharsets.UTF_8));
        ArtifactMetaData metaData = ArtifactUtils.createArtifact(registryClient, ArtifactType.AVRO, artifactId1, artifactData);
        LOGGER.info("Created artifact {} with metadata {}", artifactId1, metaData);
        ArtifactMetaData amd1 = metaData;
        TestUtils.retry(() -> registryClient.getArtifactMetaDataByGlobalId(amd1.getGlobalId()));

        Rule rule = new Rule();
        rule.setType(RuleType.VALIDITY);
        rule.setConfig("SYNTAX_ONLY");

        registryClient.createArtifactRule(artifactId1, rule);
        LOGGER.info("Created rule: {} - {} for artifact {}", rule.getType(), rule.getConfig(), artifactId1);

        registryClient.deleteArtifact(artifactId1);
        TestUtils.assertClientError(ArtifactNotFoundException.class.getSimpleName(), 404, () -> registryClient.getArtifactMetaData(artifactId1), true, errorCodeExtractor);

        assertThat(0, is(registryClient.listArtifacts().size()));

        TestUtils.assertClientError(ArtifactNotFoundException.class.getSimpleName(), 404, () -> registryClient.listArtifactRules(artifactId1), errorCodeExtractor);
        TestUtils.assertClientError(ArtifactNotFoundException.class.getSimpleName(), 404, () -> registryClient.getArtifactRuleConfig(artifactId1, RuleType.VALIDITY), errorCodeExtractor);
    }

    @AfterEach
    void clearRules() throws Exception {
        LOGGER.info("Removing all global rules");
        registryClient.deleteAllGlobalRules();
        TestUtils.retry(() -> {
            List<RuleType> rules = registryClient.listGlobalRules();
            assertEquals(0, rules.size(), "All global rules not deleted");
        });
    }
}