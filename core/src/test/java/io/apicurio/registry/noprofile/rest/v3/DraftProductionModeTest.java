package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateArtifactResponse;
import io.apicurio.registry.rest.client.models.CreateGroup;
import io.apicurio.registry.rest.client.models.CreateRule;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.IfArtifactExists;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.rest.client.models.RuleViolationProblemDetails;
import io.apicurio.registry.rest.client.models.VersionState;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.DraftProductionModeProfile;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.UUID;

/**
 * Tests for the draft production mode feature. When enabled:
 * - Draft versions use real content hashes (SHA256) instead of draft: prefix
 * - Draft versions are accessible via content lookups (contentId, globalId, contentHash)
 * - Rules are evaluated when creating draft versions
 * - ifExists=FIND_OR_CREATE_VERSION works properly with draft content
 */
@QuarkusTest
@TestProfile(DraftProductionModeProfile.class)
public class DraftProductionModeTest extends AbstractResourceTestBase {

    private static final String AVRO_CONTENT = """
            {
               "type" : "record",
               "namespace" : "Apicurio",
               "name" : "FullName",
               "fields" : [
                  { "name" : "FirstName" , "type" : "string" },
                  { "name" : "LastName" , "type" : "string" }
               ]
            }
            """;

    private static final String INVALID_AVRO_CONTENT = """
            {
               "type" : "record",
               "namespace" : "Apicurio",
               "name" : "FullName"
            """;

    /**
     * Test that draft versions use real SHA256 content hashes (not draft: prefix)
     * when draft production mode is enabled.
     */
    @Test
    public void testDraftWithRealContentHash() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, ArtifactType.AVRO,
                AVRO_CONTENT, ContentTypes.APPLICATION_JSON);
        createArtifact.getFirstVersion().setIsDraft(true);
        createArtifact.getFirstVersion().setVersion("1.0.0");

        CreateArtifactResponse car = clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);
        Assertions.assertNotNull(car);
        Assertions.assertNotNull(car.getVersion());
        Assertions.assertEquals(VersionState.DRAFT, car.getVersion().getState());

        // In production mode, contentId should be a real content ID (not based on draft: hash)
        Long contentId = car.getVersion().getContentId();
        Assertions.assertNotNull(contentId);
        Assertions.assertTrue(contentId > 0);
    }

    /**
     * Test that draft content is accessible via GET /ids/contentIds/{id}
     * when draft production mode is enabled.
     */
    @Test
    public void testDraftAccessibleByContentId() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, ArtifactType.AVRO,
                AVRO_CONTENT, ContentTypes.APPLICATION_JSON);
        createArtifact.getFirstVersion().setIsDraft(true);
        createArtifact.getFirstVersion().setVersion("1.0.0");

        CreateArtifactResponse car = clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);
        Long contentId = car.getVersion().getContentId();

        // Should be able to fetch content by contentId (allowed in production mode)
        try (InputStream content = clientV3.ids().contentIds().byContentId(contentId).get()) {
            Assertions.assertNotNull(content);
            byte[] bytes = content.readAllBytes();
            Assertions.assertTrue(bytes.length > 0);
        }
    }

    /**
     * Test that draft content is accessible via GET /ids/globalIds/{id}
     * when draft production mode is enabled.
     */
    @Test
    public void testDraftAccessibleByGlobalId() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, ArtifactType.AVRO,
                AVRO_CONTENT, ContentTypes.APPLICATION_JSON);
        createArtifact.getFirstVersion().setIsDraft(true);
        createArtifact.getFirstVersion().setVersion("1.0.0");

        CreateArtifactResponse car = clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);
        Long globalId = car.getVersion().getGlobalId();

        // Should be able to fetch content by globalId (allowed in production mode)
        try (InputStream content = clientV3.ids().globalIds().byGlobalId(globalId).get()) {
            Assertions.assertNotNull(content);
            byte[] bytes = content.readAllBytes();
            Assertions.assertTrue(bytes.length > 0);
        }
    }

    /**
     * Test that ifExists=FIND_OR_CREATE_VERSION finds an existing draft
     * when draft production mode is enabled (because drafts have real content hashes).
     */
    @Test
    public void testFindOrCreateVersionFindsDraft() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();
        // Use unique content to avoid collision with other tests
        String uniqueContent = AVRO_CONTENT.replace("FullName", "FullName" + UUID.randomUUID().toString().substring(0, 8));

        // Create artifact with first draft version
        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, ArtifactType.AVRO,
                uniqueContent, ContentTypes.APPLICATION_JSON);
        createArtifact.getFirstVersion().setIsDraft(true);
        createArtifact.getFirstVersion().setVersion("1.0.0");

        CreateArtifactResponse car1 = clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);
        Assertions.assertNotNull(car1);
        Long firstGlobalId = car1.getVersion().getGlobalId();

        // Now try to create the same artifact again with FIND_OR_CREATE_VERSION
        // It should find the existing draft version (because it has a real content hash)
        CreateArtifact createArtifact2 = TestUtils.clientCreateArtifact(artifactId, ArtifactType.AVRO,
                uniqueContent, ContentTypes.APPLICATION_JSON);
        createArtifact2.getFirstVersion().setIsDraft(true);
        createArtifact2.getFirstVersion().setVersion("1.0.1"); // Different version

        CreateArtifactResponse car2 = clientV3.groups().byGroupId(groupId).artifacts()
                .post(createArtifact2, config -> {
                    config.queryParameters.ifExists = IfArtifactExists.FIND_OR_CREATE_VERSION;
                });

        Assertions.assertNotNull(car2);
        // Should find the existing version, not create a new one
        Assertions.assertEquals(firstGlobalId, car2.getVersion().getGlobalId());
        Assertions.assertEquals("1.0.0", car2.getVersion().getVersion());
    }

    /**
     * Test that validity rules are checked when creating a draft artifact
     * when draft production mode is enabled.
     */
    @Test
    public void testRulesAppliedToDraftCreation() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        // Create group
        CreateGroup createGroup = new CreateGroup();
        createGroup.setGroupId(groupId);
        clientV3.groups().post(createGroup);

        // Enable validity group rule
        CreateRule createRule = new CreateRule();
        createRule.setRuleType(RuleType.VALIDITY);
        createRule.setConfig(ValidityLevel.FULL.name());
        clientV3.groups().byGroupId(groupId).rules().post(createRule);

        // Try to create artifact with first version that has invalid content as DRAFT
        // In production mode, rules should be applied even for drafts
        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, ArtifactType.AVRO,
                INVALID_AVRO_CONTENT, ContentTypes.APPLICATION_JSON);
        createArtifact.getFirstVersion().setIsDraft(true);
        createArtifact.getFirstVersion().setVersion("1.0.0");

        // Should fail because rules are applied in production mode
        RuleViolationProblemDetails error = Assertions.assertThrows(RuleViolationProblemDetails.class, () -> {
            clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);
        });
        Assertions.assertEquals("RuleViolationException", error.getName());
    }

    /**
     * Test that validity rules are checked when creating a draft version
     * when draft production mode is enabled.
     */
    @Test
    public void testRulesAppliedToDraftVersion() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        // Create empty artifact first
        CreateArtifact createArtifact = new CreateArtifact();
        createArtifact.setArtifactId(artifactId);
        createArtifact.setArtifactType(ArtifactType.AVRO);
        clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);

        // Enable the validity rule for the new artifact
        CreateRule createRule = new CreateRule();
        createRule.setRuleType(RuleType.VALIDITY);
        createRule.setConfig(ValidityLevel.FULL.name());
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).rules().post(createRule);

        // Try to create a new DRAFT version with invalid content
        // In production mode, rules should be applied even for drafts
        CreateVersion createVersion = TestUtils.clientCreateVersion(INVALID_AVRO_CONTENT,
                ContentTypes.APPLICATION_JSON);
        createVersion.setVersion("1.0.0");
        createVersion.setIsDraft(true);

        // Should fail because rules are applied in production mode
        RuleViolationProblemDetails error = Assertions.assertThrows(RuleViolationProblemDetails.class, () -> {
            clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                    .post(createVersion);
        });
        Assertions.assertEquals("RuleViolationException", error.getName());
    }

    /**
     * Test that valid draft content can still be created when rules are configured.
     */
    @Test
    public void testValidDraftWithRulesSucceeds() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        // Create group
        CreateGroup createGroup = new CreateGroup();
        createGroup.setGroupId(groupId);
        clientV3.groups().post(createGroup);

        // Enable validity group rule
        CreateRule createRule = new CreateRule();
        createRule.setRuleType(RuleType.VALIDITY);
        createRule.setConfig(ValidityLevel.FULL.name());
        clientV3.groups().byGroupId(groupId).rules().post(createRule);

        // Create artifact with valid DRAFT content - should succeed
        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, ArtifactType.AVRO,
                AVRO_CONTENT, ContentTypes.APPLICATION_JSON);
        createArtifact.getFirstVersion().setIsDraft(true);
        createArtifact.getFirstVersion().setVersion("1.0.0");

        CreateArtifactResponse car = clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);
        Assertions.assertNotNull(car);
        Assertions.assertNotNull(car.getVersion());
        Assertions.assertEquals(VersionState.DRAFT, car.getVersion().getState());
    }

    /**
     * Test that creating the same draft content twice results in content deduplication
     * (same contentId) when draft production mode is enabled.
     */
    @Test
    public void testDraftContentDeduplication() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId1 = TestUtils.generateArtifactId();
        String artifactId2 = TestUtils.generateArtifactId();
        // Use unique content for this test
        String uniqueContent = AVRO_CONTENT.replace("FullName", "DedupTest" + UUID.randomUUID().toString().substring(0, 8));

        // Create first artifact with draft version
        CreateArtifact createArtifact1 = TestUtils.clientCreateArtifact(artifactId1, ArtifactType.AVRO,
                uniqueContent, ContentTypes.APPLICATION_JSON);
        createArtifact1.getFirstVersion().setIsDraft(true);
        createArtifact1.getFirstVersion().setVersion("1.0.0");

        CreateArtifactResponse car1 = clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact1);
        Long contentId1 = car1.getVersion().getContentId();

        // Create second artifact with same draft content
        CreateArtifact createArtifact2 = TestUtils.clientCreateArtifact(artifactId2, ArtifactType.AVRO,
                uniqueContent, ContentTypes.APPLICATION_JSON);
        createArtifact2.getFirstVersion().setIsDraft(true);
        createArtifact2.getFirstVersion().setVersion("1.0.0");

        CreateArtifactResponse car2 = clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact2);
        Long contentId2 = car2.getVersion().getContentId();

        // Both should have the same contentId due to deduplication
        Assertions.assertEquals(contentId1, contentId2);
    }
}
