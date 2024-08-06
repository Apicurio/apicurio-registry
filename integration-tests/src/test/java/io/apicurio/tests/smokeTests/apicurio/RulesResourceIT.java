package io.apicurio.tests.smokeTests.apicurio;

import io.apicurio.registry.rest.client.models.CreateRule;
import io.apicurio.registry.rest.client.models.Rule;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.rules.compatibility.CompatibilityLevel;
import io.apicurio.registry.rules.integrity.IntegrityLevel;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.ApicurioRegistryBaseIT;
import io.apicurio.tests.utils.Constants;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        // Create a global rule (VALIDITY)
        CreateRule createRule = new CreateRule();
        createRule.setRuleType(RuleType.VALIDITY);
        createRule.setConfig(ValidityLevel.SYNTAX_ONLY.name());
        registryClient.admin().rules().post(createRule);

        // Create a global rule (INTEGRITY)
        createRule = new CreateRule();
        createRule.setRuleType(RuleType.INTEGRITY);
        createRule.setConfig(IntegrityLevel.ALL_REFS_MAPPED.name());
        registryClient.admin().rules().post(createRule);

        // Create a global rule (COMPATIBILITY)
        createRule = new CreateRule();
        createRule.setRuleType(RuleType.COMPATIBILITY);
        createRule.setConfig(CompatibilityLevel.FORWARD_TRANSITIVE.name());
        registryClient.admin().rules().post(createRule);

        // Check the rules were created.
        List<RuleType> rules = registryClient.admin().rules().get();
        assertThat(rules.size(), is(3));

        // Check the rules were configured properly.
        Rule ruleConfig = registryClient.admin().rules().byRuleType(RuleType.VALIDITY.name()).get();
        assertNotNull(ruleConfig);
        assertEquals(ValidityLevel.SYNTAX_ONLY.name(), ruleConfig.getConfig());

        ruleConfig = registryClient.admin().rules().byRuleType(RuleType.INTEGRITY.name()).get();
        assertNotNull(ruleConfig);
        assertEquals(IntegrityLevel.ALL_REFS_MAPPED.name(), ruleConfig.getConfig());

        ruleConfig = registryClient.admin().rules().byRuleType(RuleType.COMPATIBILITY.name()).get();
        assertNotNull(ruleConfig);
        assertEquals(CompatibilityLevel.FORWARD_TRANSITIVE.name(), ruleConfig.getConfig());

        // Delete all rules
        registryClient.admin().rules().delete();

        // No rules listed now
        rules = registryClient.admin().rules().get();
        assertThat(rules.size(), is(0));

        // Should be null/error (never configured the COMPATIBILITY rule)
        retryAssertClientError("RuleNotFoundException", 404,
                (rc) -> rc.admin().rules().byRuleType(RuleType.COMPATIBILITY.name()).get(),
                errorCodeExtractor);

        // Should be null/error (deleted the VALIDITY rule)
        retryAssertClientError("RuleNotFoundException", 404,
                (rc) -> rc.admin().rules().byRuleType(RuleType.VALIDITY.name()).get(), errorCodeExtractor);

        // Should be null/error (deleted the INTEGRITY rule)
        retryAssertClientError("RuleNotFoundException", 404,
                (rc) -> rc.admin().rules().byRuleType(RuleType.INTEGRITY.name()).get(), errorCodeExtractor);
    }

    @Test
    @Tag(ACCEPTANCE)
    void createAndValidateGlobalRules() throws Exception {
        String groupId = TestUtils.generateGroupId();

        CreateRule createRule = new CreateRule();
        createRule.setRuleType(RuleType.VALIDITY);
        createRule.setConfig("SYNTAX_ONLY");

        TestUtils.retry(() -> registryClient.admin().rules().post(createRule));
        LOGGER.info("Created rule: {} - {}", createRule.getRuleType(), createRule.getConfig());

        TestUtils.assertClientError("RuleAlreadyExistsException", 409,
                () -> registryClient.admin().rules().post(createRule), true, errorCodeExtractor);

        String invalidArtifactDefinition = "<type>record</type>\n<name>test</name>";
        String artifactId = TestUtils.generateArtifactId();

        LOGGER.info("Invalid artifact sent {}", invalidArtifactDefinition);
        TestUtils.assertClientError(
                "RuleViolationException", 409, () -> createArtifact(groupId, artifactId, ArtifactType.AVRO,
                        invalidArtifactDefinition, ContentTypes.APPLICATION_JSON, null, null),
                errorCodeExtractor);
        TestUtils
                .assertClientError(
                        "ArtifactNotFoundException", 404, () -> createArtifactVersion(groupId, artifactId,
                                invalidArtifactDefinition, ContentTypes.APPLICATION_JSON, null),
                        errorCodeExtractor);

        String artifactData = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"long\"}]}";

        var caResponse = createArtifact(groupId, artifactId, ArtifactType.AVRO, artifactData,
                ContentTypes.APPLICATION_JSON, null, null);
        LOGGER.info("Created artifact {} with metadata {}", artifactId, caResponse.getArtifact().toString());

        artifactData = "{\"type\":\"record\",\"name\":\"myrecord2\",\"fields\":[{\"name\":\"bar\",\"type\":\"long\"}]}";
        var metaData = createArtifactVersion(groupId, artifactId, artifactData, ContentTypes.APPLICATION_JSON,
                null);
        LOGGER.info("Artifact with Id:{} was updated:{}", artifactId, metaData.toString());

        retryOp((rc) -> {
            List<String> artifactVersions = listArtifactVersions(rc, groupId, artifactId);
            LOGGER.info("Available versions of artifact with ID {} are: {}", artifactId,
                    artifactVersions.toString());
            assertThat(artifactVersions, hasItems("1", "2"));
        });
    }

    @Test
    @Tag(ACCEPTANCE)
    void createAndValidateArtifactRule() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId1 = TestUtils.generateArtifactId();
        String artifactDefinition = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";

        String artifactData = artifactDefinition;
        var caResponse = createArtifact(groupId, artifactId1, ArtifactType.AVRO, artifactData,
                ContentTypes.APPLICATION_JSON, null, null);
        LOGGER.info("Created artifact {} with metadata {}", artifactId1, caResponse.getArtifact());

        String artifactId2 = TestUtils.generateArtifactId();
        artifactDefinition = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";

        caResponse = createArtifact(groupId, artifactId2, ArtifactType.AVRO, artifactDefinition,
                ContentTypes.APPLICATION_JSON, null, null);
        LOGGER.info("Created artifact {} with metadata {}", artifactId2, caResponse.getArtifact());

        CreateRule createRule = new CreateRule();
        createRule.setRuleType(RuleType.VALIDITY);
        createRule.setConfig("SYNTAX_ONLY");

        registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId1).rules()
                .post(createRule);
        LOGGER.info("Created rule: {} - {} for artifact {}", createRule.getRuleType(), createRule.getConfig(),
                artifactId1);

        TestUtils.assertClientError(
                "RuleAlreadyExistsException", 409, () -> registryClient.groups().byGroupId(groupId)
                        .artifacts().byArtifactId(artifactId1).rules().post(createRule),
                true, errorCodeExtractor);

        String invalidArtifactDefinition = "<type>record</type>\n<name>test</name>";
        TestUtils
                .assertClientError(
                        "RuleViolationException", 409, () -> createArtifactVersion(groupId, artifactId1,
                                invalidArtifactDefinition, ContentTypes.APPLICATION_JSON, null),
                        errorCodeExtractor);

        String updatedArtifactData = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"bar\",\"type\":\"long\"}]}";

        artifactData = updatedArtifactData;
        var metaData = createArtifactVersion(groupId, artifactId2, artifactData,
                ContentTypes.APPLICATION_JSON, null);
        LOGGER.info("Artifact with ID {} was updated: {}", artifactId2, metaData.toString());

        artifactData = updatedArtifactData;
        metaData = createArtifactVersion(groupId, artifactId1, artifactData, ContentTypes.APPLICATION_JSON,
                null);
        LOGGER.info("Artifact with ID {} was updated: {}", artifactId1, metaData.toString());

        retryOp((rc) -> {
            List<String> artifactVersions = listArtifactVersions(rc, groupId, artifactId1);
            LOGGER.info("Available versions of artifact with ID {} are: {}", artifactId1,
                    artifactVersions.toString());
            assertThat(artifactVersions, hasItems("1", "2"));

            artifactVersions = listArtifactVersions(rc, groupId, artifactId2);
            LOGGER.info("Available versions of artifact with ID {} are: {}", artifactId2,
                    artifactVersions.toString());
            assertThat(artifactVersions, hasItems("1", "2"));
        });
    }

    @Test
    @Tag(ACCEPTANCE)
    void testDeleteAllArtifactRules() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId1 = TestUtils.generateArtifactId();
        String artifactDefinition = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";

        var caResponse = createArtifact(groupId, artifactId1, ArtifactType.AVRO, artifactDefinition,
                ContentTypes.APPLICATION_JSON, null, null);
        LOGGER.info("Created artifact {} with metadata {}", artifactId1, caResponse.getArtifact());

        // Validity rule
        CreateRule createRule = new CreateRule();
        createRule.setRuleType(RuleType.VALIDITY);
        createRule.setConfig(ValidityLevel.SYNTAX_ONLY.name());
        registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId1).rules()
                .post(createRule);
        LOGGER.info("Created rule: {} - {} for artifact {}", createRule.getRuleType(), createRule.getConfig(),
                artifactId1);

        // Compatibility rule
        createRule = new CreateRule();
        createRule.setRuleType(RuleType.COMPATIBILITY);
        createRule.setConfig(CompatibilityLevel.FULL.name());
        registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId1).rules()
                .post(createRule);
        LOGGER.info("Created rule: {} - {} for artifact {}", createRule.getRuleType(), createRule.getConfig(),
                artifactId1);

        // Integrity rule
        createRule = new CreateRule();
        createRule.setRuleType(RuleType.INTEGRITY);
        createRule.setConfig(IntegrityLevel.NO_DUPLICATES.name());
        registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId1).rules()
                .post(createRule);
        LOGGER.info("Created rule: {} - {} for artifact {}", createRule.getRuleType(), createRule.getConfig(),
                artifactId1);

        // Check that all the rules exist.
        List<RuleType> rules = registryClient.groups().byGroupId(groupId).artifacts()
                .byArtifactId(artifactId1).rules().get();
        assertThat(rules.size(), is(3));

        // Check that the Integrity rule is configured
        Rule rule = registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId1).rules()
                .byRuleType(RuleType.INTEGRITY.name()).get();
        assertThat(rule.getConfig(), is(IntegrityLevel.NO_DUPLICATES.name()));

        // Delete all rules.
        registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId1).rules().delete();

        // Check that no rules exist.
        rules = registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId1).rules()
                .get();
        assertThat(rules.size(), is(0));

        // Check that the integrity rule is not found.
        TestUtils.assertClientError("RuleNotFoundException", 404, () -> {
            registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId1).rules()
                    .byRuleType(RuleType.INTEGRITY.name()).get();
        }, errorCodeExtractor);
    }

    @Test
    @Tag(ACCEPTANCE)
    void testRulesDeletedWithArtifact() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId1 = TestUtils.generateArtifactId();
        String artifactDefinition = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";

        var caResponse = createArtifact(groupId, artifactId1, ArtifactType.AVRO, artifactDefinition,
                ContentTypes.APPLICATION_JSON, null, null);
        LOGGER.info("Created artifact {} with metadata {}", artifactId1, caResponse.getArtifact());

        CreateRule createRule = new CreateRule();
        createRule.setRuleType(RuleType.VALIDITY);
        createRule.setConfig("SYNTAX_ONLY");

        registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId1).rules()
                .post(createRule);
        LOGGER.info("Created rule: {} - {} for artifact {}", createRule.getRuleType(), createRule.getConfig(),
                artifactId1);

        registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId1).delete();

        retryOp((rc) -> {
            TestUtils.assertClientError("ArtifactNotFoundException", 404,
                    () -> rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId1).get(),
                    errorCodeExtractor);

            assertThat(rc.groups().byGroupId(groupId).artifacts().get().getCount(), is(0));

            TestUtils.assertClientError("ArtifactNotFoundException", 404,
                    () -> rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId1).rules().get(),
                    errorCodeExtractor);
            TestUtils.assertClientError(
                    "ArtifactNotFoundException", 404, () -> rc.groups().byGroupId(groupId).artifacts()
                            .byArtifactId(artifactId1).rules().byRuleType(RuleType.VALIDITY.name()).get(),
                    errorCodeExtractor);
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