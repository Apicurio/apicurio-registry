package io.apicurio.tests.smokeTests.apicurio;

import io.apicurio.registry.types.ArtifactType;
import io.apicurio.tests.ApicurioRegistryBaseIT;
import io.apicurio.tests.utils.Constants;
import io.apicurio.registry.rest.client.models.ArtifactMetaData;
import io.apicurio.registry.rest.client.models.Rule;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Tag(Constants.SMOKE)
@QuarkusIntegrationTest
class RulesResourceIT extends ApicurioRegistryBaseIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(RulesResourceIT.class);

    @Test
    void createAndDeleteGlobalRules() throws Exception {
        // Create a global rule
        Rule rule = new Rule();
        rule.setType(RuleType.VALIDITY);
        rule.setConfig("SYNTAX_ONLY");

        TestUtils.retry(() -> registryClient.admin().rules().post(rule));

        // Check the rule was created.
        retryOp((rc) -> {
            Rule ruleConfig = rc.admin().rules().byRule(RuleType.VALIDITY.name()).get();
            assertNotNull(ruleConfig);
            assertEquals("SYNTAX_ONLY", ruleConfig.getConfig());
        });

        // Delete all rules
        registryClient.admin().rules().delete();

        // No rules listed now
        retryOp((rc) -> {
            List<RuleType> rules = rc.admin().rules().get();
            assertEquals(0, rules.size());
        });

        // Should be null/error (never configured the COMPATIBILITY rule)
        retryAssertClientError("RuleNotFoundException", 404, (rc) -> rc.admin().rules().byRule(RuleType.COMPATIBILITY.name()).get(), errorCodeExtractor);

        // Should be null/error (deleted the VALIDITY rule)
        retryAssertClientError("RuleNotFoundException", 404, (rc) -> rc.admin().rules().byRule(RuleType.COMPATIBILITY.name()).get(), errorCodeExtractor);
    }

    @Test
    @Tag(ACCEPTANCE)
    void createAndValidateGlobalRules() throws Exception {
        String groupId = TestUtils.generateGroupId();

        Rule rule = new Rule();
        rule.setType(RuleType.VALIDITY);
        rule.setConfig("SYNTAX_ONLY");

        TestUtils.retry(() -> registryClient.admin().rules().post(rule));
        LOGGER.info("Created rule: {} - {}", rule.getType(), rule.getConfig());

        TestUtils.assertClientError("RuleAlreadyExistsException", 409, () -> registryClient.admin().rules().post(rule), true, errorCodeExtractor);

        String invalidArtifactDefinition = "<type>record</type>\n<name>test</name>";
        String artifactId = TestUtils.generateArtifactId();

        LOGGER.info("Invalid artifact sent {}", invalidArtifactDefinition);
        TestUtils.assertClientError("RuleViolationException", 409, () -> createArtifact(groupId, artifactId, ArtifactType.AVRO, IoUtil.toStream(invalidArtifactDefinition)), errorCodeExtractor);
        TestUtils.assertClientError("ArtifactNotFoundException", 404, () -> updateArtifact(groupId, artifactId, IoUtil.toStream(invalidArtifactDefinition)), errorCodeExtractor);

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

        registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId1).rules().post(rule);
        LOGGER.info("Created rule: {} - {} for artifact {}", rule.getType(), rule.getConfig(), artifactId1);

        TestUtils.assertClientError("RuleAlreadyExistsException", 409, () -> registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId1).rules().post(rule), true, errorCodeExtractor);

        String invalidArtifactDefinition = "<type>record</type>\n<name>test</name>";
        artifactData = new ByteArrayInputStream(invalidArtifactDefinition.getBytes(StandardCharsets.UTF_8));

        ByteArrayInputStream iad = artifactData;
        TestUtils.assertClientError("RuleViolationException", 409, () -> updateArtifact(groupId, artifactId1, iad), errorCodeExtractor);

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

        registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId1).rules().post(rule);
        LOGGER.info("Created rule: {} - {} for artifact {}", rule.getType(), rule.getConfig(), artifactId1);

        registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId1).delete();

        retryOp((rc) -> {
            TestUtils.assertClientError("ArtifactNotFoundException", 404, () -> rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId1).meta().get(), errorCodeExtractor);

            assertThat(rc.groups().byGroupId(groupId).artifacts().get().getCount(), is(0));

            TestUtils.assertClientError("ArtifactNotFoundException", 404, () ->
                    rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId1).rules().get(), errorCodeExtractor);
            TestUtils.assertClientError("ArtifactNotFoundException", 404, () ->
                    rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId1).rules().byRule(RuleType.VALIDITY.name()).get(), errorCodeExtractor);
        });
    }

    @AfterEach
    void clearRules() throws Exception {
        LOGGER.info("Removing all global rules");
        registryClient.admin().rules().delete();
        retryOp((rc) -> {
            List<RuleType> rules = rc.admin().rules().get();
            assertEquals(0, rules.size(), "All global rules not deleted");
        });
    }
}