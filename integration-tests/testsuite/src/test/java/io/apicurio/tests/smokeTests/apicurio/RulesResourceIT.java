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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.hasItems;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.apicurio.registry.rest.v2.beans.ArtifactMetaData;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.ApicurioV2BaseIT;
import io.apicurio.tests.common.Constants;

import java.util.List;

import io.apicurio.registry.rest.client.exception.ArtifactNotFoundException;
import io.apicurio.registry.rest.client.exception.RuleAlreadyExistsException;
import io.apicurio.registry.rest.client.exception.RuleNotFoundException;
import io.apicurio.registry.rest.client.exception.RuleViolationException;
import io.apicurio.registry.rest.v2.beans.Rule;
import io.apicurio.registry.types.RuleType;

@Tag(Constants.SMOKE)
class RulesResourceIT extends ApicurioV2BaseIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(RulesResourceIT.class);

    @Test
    void createAndDeleteGlobalRules() throws Exception {
        // Create a global rule
        Rule rule = new Rule();
        rule.setType(RuleType.VALIDITY);
        rule.setConfig("SYNTAX_ONLY");

        TestUtils.retry(() -> registryClient.createGlobalRule(rule));

        // Check the rule was created.
        retryOp((rc) -> {
            Rule ruleConfig = rc.getGlobalRuleConfig(RuleType.VALIDITY);
            assertNotNull(ruleConfig);
            assertEquals("SYNTAX_ONLY", ruleConfig.getConfig());
        });

        // Delete all rules
        registryClient.deleteAllGlobalRules();

        // No rules listed now
        retryOp((rc) -> {
            List<RuleType> rules = rc.listGlobalRules();
            assertEquals(0, rules.size());
        });

        // Should be null/error (never configured the COMPATIBILITY rule)
        retryAssertClientError(RuleNotFoundException.class.getSimpleName(), 404, (rc) -> rc.getGlobalRuleConfig(RuleType.COMPATIBILITY), errorCodeExtractor);

        // Should be null/error (deleted the VALIDITY rule)
        retryAssertClientError(RuleNotFoundException.class.getSimpleName(), 404, (rc) -> rc.getGlobalRuleConfig(RuleType.VALIDITY), errorCodeExtractor);
    }

    @Test
    @Tag(ACCEPTANCE)
    void createAndValidateGlobalRules() throws Exception {
        String groupId = TestUtils.generateGroupId();

        Rule rule = new Rule();
        rule.setType(RuleType.VALIDITY);
        rule.setConfig("SYNTAX_ONLY");

        TestUtils.retry(() -> registryClient.createGlobalRule(rule));
        LOGGER.info("Created rule: {} - {}", rule.getType(), rule.getConfig());

        TestUtils.assertClientError(RuleAlreadyExistsException.class.getSimpleName(), 409, () -> registryClient.createGlobalRule(rule), true, errorCodeExtractor);

        String invalidArtifactDefinition = "<type>record</type>\n<name>test</name>";
        String artifactId = TestUtils.generateArtifactId();

        LOGGER.info("Invalid artifact sent {}", invalidArtifactDefinition);
        TestUtils.assertClientError(RuleViolationException.class.getSimpleName(), 409, () -> createArtifact(groupId, artifactId, ArtifactType.AVRO, IoUtil.toStream(invalidArtifactDefinition)), errorCodeExtractor);
        TestUtils.assertClientError(ArtifactNotFoundException.class.getSimpleName(), 404, () -> updateArtifact(groupId, artifactId, IoUtil.toStream(invalidArtifactDefinition)), errorCodeExtractor);

        ByteArrayInputStream artifactData = new ByteArrayInputStream("{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"long\"}]}".getBytes(StandardCharsets.UTF_8));

        ArtifactMetaData metaData = createArtifact(groupId, artifactId, ArtifactType.AVRO, artifactData);
        LOGGER.info("Created artifact {} with metadata {}", artifactId, metaData.toString());

        artifactData = new ByteArrayInputStream("{\"type\":\"record\",\"name\":\"myrecord2\",\"fields\":[{\"name\":\"bar\",\"type\":\"long\"}]}".getBytes(StandardCharsets.UTF_8));
        metaData = updateArtifact(groupId, artifactId, artifactData);
        LOGGER.info("Artifact with Id:{} was updated:{}", artifactId, metaData.toString());

        retryOp((rc) -> {
            List<String> artifactVersions = listArtifactVersions(rc, groupId, artifactId);
            LOGGER.info("Available versions of artifact with ID {} are: {}", artifactId, artifactVersions.toString());
            assertThat(artifactVersions, hasItems("1", "2"));
        });
    }

    @Test
    @Tag(ACCEPTANCE)
    void createAndValidateArtifactRule() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId1 = TestUtils.generateArtifactId();
        String artifactDefinition = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";

        ByteArrayInputStream artifactData = new ByteArrayInputStream(artifactDefinition.getBytes(StandardCharsets.UTF_8));
        ArtifactMetaData metaData = createArtifact(groupId, artifactId1, ArtifactType.AVRO, artifactData);
        LOGGER.info("Created artifact {} with metadata {}", artifactId1, metaData);

        String artifactId2 = TestUtils.generateArtifactId();
        artifactDefinition = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";
        artifactData = new ByteArrayInputStream(artifactDefinition.getBytes(StandardCharsets.UTF_8));

        metaData = createArtifact(groupId, artifactId2, ArtifactType.AVRO, artifactData);
        LOGGER.info("Created artifact {} with metadata {}", artifactId2, metaData);

        Rule rule = new Rule();
        rule.setType(RuleType.VALIDITY);
        rule.setConfig("SYNTAX_ONLY");

        registryClient.createArtifactRule(groupId, artifactId1, rule);
        LOGGER.info("Created rule: {} - {} for artifact {}", rule.getType(), rule.getConfig(), artifactId1);

        TestUtils.assertClientError(RuleAlreadyExistsException.class.getSimpleName(), 409, () -> registryClient.createArtifactRule(groupId, artifactId1, rule), true, errorCodeExtractor);

        String invalidArtifactDefinition = "<type>record</type>\n<name>test</name>";
        artifactData = new ByteArrayInputStream(invalidArtifactDefinition.getBytes(StandardCharsets.UTF_8));

        ByteArrayInputStream iad = artifactData;
        TestUtils.assertClientError(RuleViolationException.class.getSimpleName(), 409, () -> updateArtifact(groupId, artifactId1, iad), errorCodeExtractor);

        String updatedArtifactData = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"bar\",\"type\":\"long\"}]}";

        artifactData = new ByteArrayInputStream(updatedArtifactData.getBytes(StandardCharsets.UTF_8));
        metaData = updateArtifact(groupId, artifactId2, artifactData);
        LOGGER.info("Artifact with ID {} was updated: {}", artifactId2, metaData.toString());

        artifactData = new ByteArrayInputStream(updatedArtifactData.getBytes(StandardCharsets.UTF_8));
        metaData = updateArtifact(groupId, artifactId1, artifactData);
        LOGGER.info("Artifact with ID {} was updated: {}", artifactId1, metaData.toString());

        retryOp((rc) -> {
            List<String> artifactVersions = listArtifactVersions(rc, groupId, artifactId1);
            LOGGER.info("Available versions of artifact with ID {} are: {}", artifactId1, artifactVersions.toString());
            assertThat(artifactVersions, hasItems("1", "2"));

            artifactVersions = listArtifactVersions(rc, groupId, artifactId2);
            LOGGER.info("Available versions of artifact with ID {} are: {}", artifactId2, artifactVersions.toString());
            assertThat(artifactVersions, hasItems("1", "2"));
        });
    }

    @Test
    @Tag(ACCEPTANCE)
    void testRulesDeletedWithArtifact() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId1 = TestUtils.generateArtifactId();
        String artifactDefinition = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";

        ByteArrayInputStream artifactData = new ByteArrayInputStream(artifactDefinition.getBytes(StandardCharsets.UTF_8));
        ArtifactMetaData metaData = createArtifact(groupId, artifactId1, ArtifactType.AVRO, artifactData);
        LOGGER.info("Created artifact {} with metadata {}", artifactId1, metaData);

        Rule rule = new Rule();
        rule.setType(RuleType.VALIDITY);
        rule.setConfig("SYNTAX_ONLY");

        registryClient.createArtifactRule(groupId, artifactId1, rule);
        LOGGER.info("Created rule: {} - {} for artifact {}", rule.getType(), rule.getConfig(), artifactId1);

        registryClient.deleteArtifact(groupId, artifactId1);

        retryOp((rc) -> {
            TestUtils.assertClientError(ArtifactNotFoundException.class.getSimpleName(), 404, () -> rc.getArtifactMetaData(groupId, artifactId1), errorCodeExtractor);

            assertThat(rc.listArtifactsInGroup(groupId).getCount(), is(0));

            TestUtils.assertClientError(ArtifactNotFoundException.class.getSimpleName(), 404, () -> rc.listArtifactRules(groupId, artifactId1), errorCodeExtractor);
            TestUtils.assertClientError(ArtifactNotFoundException.class.getSimpleName(), 404, () -> rc.getArtifactRuleConfig(groupId, artifactId1, RuleType.VALIDITY), errorCodeExtractor);
        });
    }

    @AfterEach
    void clearRules() throws Exception {
        LOGGER.info("Removing all global rules");
        registryClient.deleteAllGlobalRules();
        retryOp((rc) -> {
            List<RuleType> rules = rc.listGlobalRules();
            assertEquals(0, rules.size(), "All global rules not deleted");
        });
    }
}