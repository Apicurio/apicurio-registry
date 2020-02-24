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
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.ws.rs.WebApplicationException;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.apicurio.registry.rest.beans.ArtifactMetaData;
import io.apicurio.registry.rest.beans.Rule;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;
import io.apicurio.tests.BaseIT;
import io.apicurio.tests.utils.subUtils.ArtifactUtils;

@Tag(SMOKE)
class RulesResourceIT extends BaseIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(RulesResourceIT.class);
    
    @Test
    void createAndDeleteGlobalRules() {
        // Create a global rule
        Rule rule = new Rule();
        rule.setType(RuleType.VALIDITY);
        rule.setConfig("SYNTAX_ONLY");
        apicurioService.createGlobalRule(rule);
        
        // Check the rule was created.
        Rule ruleConfig = apicurioService.getGlobalRuleConfig(RuleType.VALIDITY);
        assertNotNull(ruleConfig);
        assertEquals("SYNTAX_ONLY", ruleConfig.getConfig());
        
        // Delete all rules
        apicurioService.deleteAllGlobalRules();
        
        // No rules listed now
        List<RuleType> rules = apicurioService.listGlobalRules();
        assertEquals(0, rules.size());
        
        // Should be null/error (never configured the COMPATIBILITY rule)
        try {
            ruleConfig = apicurioService.getGlobalRuleConfig(RuleType.COMPATIBILITY);
            fail("Expected 404");
        } catch (WebApplicationException e) {
            assertEquals(404, e.getResponse().getStatus());
        }

        // Should be null/error (deleted the VALIDITY rule)
        try {
            ruleConfig = apicurioService.getGlobalRuleConfig(RuleType.VALIDITY);
            fail("Expected 404");
        } catch (WebApplicationException e) {
            assertEquals(404, e.getResponse().getStatus());
        }
    }

    @Test
    void createAndValidateGlobalRules() {
        Rule rule = new Rule();
        rule.setType(RuleType.VALIDITY);
        rule.setConfig("SYNTAX_ONLY");

        apicurioService.createGlobalRule(rule);
        LOGGER.info("Created rule: {} - {}", rule.getType(), rule.getConfig());

        try {
            apicurioService.createGlobalRule(rule);
        } catch (WebApplicationException e) {
            assertThat("{\"message\":\"A rule named 'VALIDITY' already exists.\",\"error_code\":409}", is(e.getResponse().readEntity(String.class)));
        }

        String invalidArtifactDefinition = "<type>record</type>\n<name>test</name>";
        ByteArrayInputStream artifactData = new ByteArrayInputStream(invalidArtifactDefinition.getBytes(StandardCharsets.UTF_8));
        String artifactId = "artifactNameId";

        try {
            LOGGER.info("Invalid artifact sent {}", invalidArtifactDefinition);
            ArtifactUtils.createArtifact(apicurioService, ArtifactType.AVRO, artifactId, artifactData);
        } catch (WebApplicationException e) {
            assertThat("{\"message\":\"Syntax violation for Avro artifact.\",\"error_code\":400}", is(e.getResponse().readEntity(String.class)));
        }

        artifactData = new ByteArrayInputStream(invalidArtifactDefinition.getBytes(StandardCharsets.UTF_8));

        try {
            ArtifactUtils.updateArtifact(apicurioService, ArtifactType.AVRO, artifactId, artifactData);
        } catch (WebApplicationException e) {
            assertThat("{\"message\":\"No artifact with ID 'artifactNameId' was found.\",\"error_code\":404}", is(e.getResponse().readEntity(String.class)));
        }

        artifactData = new ByteArrayInputStream("{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"long\"}]}".getBytes(StandardCharsets.UTF_8));

        ArtifactMetaData metaData = ArtifactUtils.createArtifact(apicurioService, ArtifactType.AVRO, artifactId, artifactData);
        LOGGER.info("Created artifact {} with metadata {}", artifactId, metaData.toString());

        artifactData = new ByteArrayInputStream("{\"type\":\"record\",\"name\":\"myrecord2\",\"fields\":[{\"name\":\"bar\",\"type\":\"long\"}]}".getBytes(StandardCharsets.UTF_8));
        metaData = ArtifactUtils.updateArtifact(apicurioService, ArtifactType.AVRO, artifactId, artifactData);
        LOGGER.info("Artifact with Id:{} was updated:{}", artifactId, metaData.toString());

        List<Long> artifactVersions = apicurioService.listArtifactVersions(artifactId);

        LOGGER.info("Available versions of artifact with ID {} are: {}", artifactId, artifactVersions.toString());
        assertThat(artifactVersions, hasItems(1L, 2L));
    }

    @Test
    void createAndValidateArtifactRule() {
        String artifactId1 = "artifactValidateRuleId1";
        String artifactDefinition = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";

        ByteArrayInputStream artifactData = new ByteArrayInputStream(artifactDefinition.getBytes(StandardCharsets.UTF_8));
        ArtifactMetaData metaData = ArtifactUtils.createArtifact(apicurioService, ArtifactType.AVRO, artifactId1, artifactData);
        LOGGER.info("Created artifact {} with metadata {}", artifactId1, metaData.toString());

        String artifactId2 = "artifactValidateRuleId2";
        artifactDefinition = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";
        artifactData = new ByteArrayInputStream(artifactDefinition.getBytes(StandardCharsets.UTF_8));

        metaData = ArtifactUtils.createArtifact(apicurioService, ArtifactType.AVRO, artifactId2, artifactData);
        LOGGER.info("Created artifact {} with metadata {}", artifactId2, metaData.toString());

        Rule rule = new Rule();
        rule.setType(RuleType.VALIDITY);
        rule.setConfig("SYNTAX_ONLY");

        apicurioService.createArtifactRule(artifactId1, rule);
        LOGGER.info("Created rule: {} - {} for artifact {}", rule.getType(), rule.getConfig(), artifactId1);

        try {
            apicurioService.createArtifactRule(artifactId1, rule);
        } catch (WebApplicationException e) {
            assertThat("{\"message\":\"A rule named 'VALIDITY' already exists.\",\"error_code\":409}", is(e.getResponse().readEntity(String.class)));
        }

        String invalidArtifactDefinition = "<type>record</type>\n<name>test</name>";
        artifactData = new ByteArrayInputStream(invalidArtifactDefinition.getBytes(StandardCharsets.UTF_8));

        try {
            ArtifactUtils.updateArtifact(apicurioService, ArtifactType.AVRO, artifactId1, artifactData);
        } catch (WebApplicationException e) {
            assertThat("{\"message\":\"Syntax violation for Avro artifact.\",\"error_code\":400}", is(e.getResponse().readEntity(String.class)));
        }

        String updatedArtifactData = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"bar\",\"type\":\"long\"}]}";

        artifactData = new ByteArrayInputStream(updatedArtifactData.getBytes(StandardCharsets.UTF_8));
        metaData = ArtifactUtils.updateArtifact(apicurioService, ArtifactType.AVRO, artifactId2, artifactData);
        LOGGER.info("Artifact with ID {} was updated: {}", artifactId2, metaData.toString());

        artifactData = new ByteArrayInputStream(updatedArtifactData.getBytes(StandardCharsets.UTF_8));
        metaData = ArtifactUtils.updateArtifact(apicurioService, ArtifactType.AVRO, artifactId1, artifactData);
        LOGGER.info("Artifact with ID {} was updated: {}", artifactId1, metaData.toString());

        List<Long> artifactVersions = apicurioService.listArtifactVersions(artifactId1);
        LOGGER.info("Available versions of artifact with ID {} are: {}", artifactId1, artifactVersions.toString());
        assertThat(artifactVersions, hasItems(1L, 2L));

        artifactVersions = apicurioService.listArtifactVersions(artifactId2);
        LOGGER.info("Available versions of artifact with ID {} are: {}", artifactId2, artifactVersions.toString());
        assertThat(artifactVersions, hasItems(1L, 2L));
    }
}
