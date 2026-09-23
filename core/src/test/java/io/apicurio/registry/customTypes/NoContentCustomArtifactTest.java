package io.apicurio.registry.customTypes;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.ArtifactReference;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateArtifactResponse;
import io.apicurio.registry.rest.client.models.CreateGroup;
import io.apicurio.registry.rest.client.models.CreateRule;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.EditableVersionMetaData;
import io.apicurio.registry.rest.client.models.ProblemDetails;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.rest.client.models.RuleViolationProblemDetails;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.rules.integrity.IntegrityLevel;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

@QuarkusTest
@TestProfile(NoContentCustomArtifactTestProfile.class)
public class NoContentCustomArtifactTest extends AbstractResourceTestBase {

    @Test
    public void testCreateArtifact() {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();
        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, "NO_CONTENT");
        createArtifact.getFirstVersion().setVersion("1.0");

        CreateArtifactResponse car = clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);
        Assertions.assertNotNull(car);
        Assertions.assertNotNull(car.getArtifact());
        Assertions.assertNotNull(car.getVersion());
        Assertions.assertEquals("NO_CONTENT", car.getArtifact().getArtifactType());

        // Should not be able to get the content.
        Assertions.assertThrows(ProblemDetails.class, () -> {
            clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression("branch=latest").content().get();
        });
        Assertions.assertThrows(ProblemDetails.class, () -> {
            clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression("1.0").content().get();
        });
        Assertions.assertThrows(ProblemDetails.class, () -> {
            clientV3.ids().contentIds().byContentId(car.getVersion().getContentId()).get();
        });
    }

    @Test
    public void testCreateVersion() {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();
        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, "NO_CONTENT");
        createArtifact.setFirstVersion(null);

        // Create an empty artifact
        clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);

        // Create a new version
        CreateVersion createVersion = new CreateVersion();
        createVersion.setVersion("1.0");
        createVersion.setName("Test Version 1.0");
        VersionMetaData versionMetaData = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().post(createVersion);
        Assertions.assertEquals("1.0", versionMetaData.getVersion());

        // Should not be able to get the content.
        Assertions.assertThrows(ProblemDetails.class, () -> {
            clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression("branch=latest").content().get();
        });
    }

    @Test
    public void testVersionMetadata() {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();
        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, "NO_CONTENT");
        createArtifact.getFirstVersion().setVersion("1.0");
        createArtifact.getFirstVersion().setName("Test Version 1.0");
        createArtifact.getFirstVersion().setDescription("Test description.");

        // Create artifact with first version (empty)
        clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);

        // Get metadata
        VersionMetaData vmd = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression("1.0").get();
        Assertions.assertEquals("1.0", vmd.getVersion());
        Assertions.assertEquals("Test Version 1.0", vmd.getName());
        Assertions.assertEquals("Test description.", vmd.getDescription());

        // Update the metadata
        EditableVersionMetaData evmd = new EditableVersionMetaData();
        evmd.setName("Updated Version");
        evmd.setDescription("Updated description.");
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression("1.0").put(evmd);

        // Verify the updates
        vmd = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression("1.0").get();
        Assertions.assertEquals("1.0", vmd.getVersion());
        Assertions.assertEquals("Updated Version", vmd.getName());
        Assertions.assertEquals("Updated description.", vmd.getDescription());
    }

    @Test
    public void testArtifactWithReferences() {
        String groupId = TestUtils.generateGroupId();
        String taArtifactId = TestUtils.generateArtifactId();
        String saArtifactId = TestUtils.generateArtifactId();

        // Create TA (target artifact)
        CreateArtifact createTA = TestUtils.clientCreateArtifact(taArtifactId, "NO_CONTENT");
        createTA.getFirstVersion().setVersion("1.0");
        clientV3.groups().byGroupId(groupId).artifacts().post(createTA);

        // Create SA (source artifact) with reference to TA
        CreateArtifact createSA = TestUtils.clientCreateArtifact(saArtifactId, "NO_CONTENT");
        createSA.getFirstVersion().setVersion("1.0");
        ArtifactReference artifactReference = new ArtifactReference();
        artifactReference.setName(taArtifactId);
        artifactReference.setGroupId(groupId);
        artifactReference.setArtifactId(taArtifactId);
        artifactReference.setVersion("1.0");
        createSA.getFirstVersion().setContent(new VersionContent());
        createSA.getFirstVersion().getContent().setReferences(List.of(artifactReference));
        clientV3.groups().byGroupId(groupId).artifacts().post(createSA);

        // Check references
        List<ArtifactReference> references = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(saArtifactId)
                .versions().byVersionExpression("1.0").references().get();
        Assertions.assertEquals(1, references.size());
    }

    @Test
    public void testArtifactWithReferencesPlusIntegrity() {
        String groupId = TestUtils.generateGroupId();
        String taArtifactId = TestUtils.generateArtifactId();
        String saArtifactId = TestUtils.generateArtifactId();

        // Create the group
        CreateGroup createGroup = new CreateGroup();
        createGroup.setGroupId(groupId);
        clientV3.groups().post(createGroup);

        // Enable Integrity rule
        CreateRule createIntegrityRule = new CreateRule();
        createIntegrityRule.setConfig(IntegrityLevel.FULL.name());
        createIntegrityRule.setRuleType(RuleType.INTEGRITY);
        clientV3.groups().byGroupId(groupId).rules().post(createIntegrityRule);

        // Create TA (target artifact)
        CreateArtifact createTA = TestUtils.clientCreateArtifact(taArtifactId, "NO_CONTENT");
        createTA.getFirstVersion().setVersion("1.0");
        clientV3.groups().byGroupId(groupId).artifacts().post(createTA);

        // Create SA (source artifact) with reference to TA
        CreateArtifact createSA = TestUtils.clientCreateArtifact(saArtifactId, "NO_CONTENT");
        createSA.getFirstVersion().setVersion("1.0");
        ArtifactReference artifactReference = new ArtifactReference();
        artifactReference.setName(taArtifactId);
        artifactReference.setGroupId(groupId);
        artifactReference.setArtifactId(taArtifactId);
        artifactReference.setVersion("1.0");
        createSA.getFirstVersion().setContent(new VersionContent());
        createSA.getFirstVersion().getContent().setReferences(List.of(artifactReference));
        clientV3.groups().byGroupId(groupId).artifacts().post(createSA);

        // Create SA v2 with reference to missing artifact
        CreateVersion sa_v2 = new CreateVersion();
        sa_v2.setVersion("2.0");
        ArtifactReference invalidArtifactRef = new ArtifactReference();
        invalidArtifactRef.setName(taArtifactId);
        invalidArtifactRef.setGroupId(groupId);
        invalidArtifactRef.setArtifactId(taArtifactId);
        invalidArtifactRef.setVersion("7.0");
        sa_v2.setContent(new VersionContent());
        sa_v2.getContent().setReferences(List.of(artifactReference, invalidArtifactRef));
        Assertions.assertThrows(RuleViolationProblemDetails.class, () -> {
            clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(taArtifactId).versions().post(sa_v2);
        });
    }

}
