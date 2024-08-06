package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.ArtifactSearchResults;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateArtifactResponse;
import io.apicurio.registry.rest.client.models.CreateGroup;
import io.apicurio.registry.rest.client.models.CreateRule;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.Error;
import io.apicurio.registry.rest.client.models.IfArtifactExists;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.rest.client.models.VersionSearchResults;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.DeletionEnabledProfile;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(DeletionEnabledProfile.class)
public class DryRunTest extends AbstractResourceTestBase {

    private static final String SCHEMA_SIMPLE = """
            {
                 "type": "record",
                 "namespace": "com.example",
                 "name": "FullName",
                 "fields": [
                   { "name": "first", "type": "string" },
                   { "name": "last", "type": "string" }
                 ]
            }
            """;
    private static final String INVALID_SCHEMA = "{\"type\": \"string";

    @Test
    public void testCreateArtifactDryRun() throws Exception {
        String groupId = "testCreateArtifactDryRun";

        // Create a group
        CreateGroup createGroup = new CreateGroup();
        createGroup.setGroupId(groupId);
        clientV3.groups().post(createGroup);

        // Add a rule
        CreateRule createRule = new CreateRule();
        createRule.setRuleType(RuleType.VALIDITY);
        createRule.setConfig(ValidityLevel.FULL.name());
        clientV3.groups().byGroupId(groupId).rules().post(createRule);

        // Dry run: valid artifact that should be created.
        CreateArtifact createArtifact = TestUtils.clientCreateArtifact("valid-artifact", ArtifactType.AVRO,
                SCHEMA_SIMPLE, ContentTypes.APPLICATION_JSON);
        CreateArtifactResponse car = clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact,
                config -> {
                    config.queryParameters.dryRun = true;
                });
        Assertions.assertNotNull(car);
        Assertions.assertEquals(groupId, car.getArtifact().getGroupId());
        Assertions.assertEquals("valid-artifact", car.getArtifact().getArtifactId());
        Assertions.assertEquals(ArtifactType.AVRO, car.getArtifact().getArtifactType());
        Assertions.assertEquals("1", car.getVersion().getVersion());

        // Assert that the artifact was NOT created
        ArtifactSearchResults results = clientV3.groups().byGroupId(groupId).artifacts().get();
        Assertions.assertEquals(0, results.getCount());
        Assertions.assertEquals(0, results.getArtifacts().size());
        Error error = Assertions.assertThrows(Error.class, () -> {
            clientV3.groups().byGroupId(groupId).artifacts().byArtifactId("valid-artifact").get();
        });
        Assertions.assertEquals(
                "No artifact with ID 'valid-artifact' in group 'testCreateArtifactDryRun' was found.",
                error.getMessageEscaped());

        // Dry run: invalid artifact that should NOT be created.
        error = Assertions.assertThrows(Error.class, () -> {
            CreateArtifact ca = TestUtils.clientCreateArtifact("invalid-artifact", ArtifactType.AVRO,
                    INVALID_SCHEMA, ContentTypes.APPLICATION_JSON);
            clientV3.groups().byGroupId(groupId).artifacts().post(ca, config -> {
                config.queryParameters.dryRun = true;
            });
        });
        Assertions.assertEquals("Syntax violation for Avro artifact.", error.getMessageEscaped());
        Assertions.assertNotNull(car);
        Assertions.assertEquals(groupId, car.getArtifact().getGroupId());
        Assertions.assertEquals("valid-artifact", car.getArtifact().getArtifactId());
        Assertions.assertEquals(ArtifactType.AVRO, car.getArtifact().getArtifactType());

        // Assert that the artifact was NOT created
        results = clientV3.groups().byGroupId(groupId).artifacts().get();
        Assertions.assertEquals(0, results.getCount());
        Assertions.assertEquals(0, results.getArtifacts().size());
        error = Assertions.assertThrows(Error.class, () -> {
            clientV3.groups().byGroupId(groupId).artifacts().byArtifactId("invalid-artifact").get();
        });
        Assertions.assertEquals(
                "No artifact with ID 'invalid-artifact' in group 'testCreateArtifactDryRun' was found.",
                error.getMessageEscaped());

        // Actually create an artifact in the group.
        createArtifact(groupId, "actual-artifact", ArtifactType.AVRO, SCHEMA_SIMPLE,
                ContentTypes.APPLICATION_JSON);
        results = clientV3.groups().byGroupId(groupId).artifacts().get();
        Assertions.assertEquals(1, results.getCount());
        Assertions.assertEquals(1, results.getArtifacts().size());

        // DryRun: Try to create the *same* artifact (conflict)
        error = Assertions.assertThrows(Error.class, () -> {
            CreateArtifact ca = TestUtils.clientCreateArtifact("actual-artifact", ArtifactType.AVRO,
                    SCHEMA_SIMPLE, ContentTypes.APPLICATION_JSON);
            clientV3.groups().byGroupId(groupId).artifacts().post(ca, config -> {
                config.queryParameters.dryRun = true;
            });
        });
        Assertions.assertEquals(
                "An artifact with ID 'actual-artifact' in group 'testCreateArtifactDryRun' already exists.",
                error.getMessageEscaped());

        // DryRun: Try to create the *same* artifact but with ifExists set (success)
        createArtifact = TestUtils.clientCreateArtifact("actual-artifact", ArtifactType.AVRO, SCHEMA_SIMPLE,
                ContentTypes.APPLICATION_JSON);
        car = clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact, config -> {
            config.queryParameters.dryRun = true;
            config.queryParameters.ifExists = IfArtifactExists.CREATE_VERSION;
        });
        Assertions.assertNotNull(car);
        Assertions.assertEquals(groupId, car.getArtifact().getGroupId());
        Assertions.assertEquals("actual-artifact", car.getArtifact().getArtifactId());
        Assertions.assertEquals(ArtifactType.AVRO, car.getArtifact().getArtifactType());
        Assertions.assertEquals("2", car.getVersion().getVersion());

        // Validate that there is only 1 artifact in the group still, but it has 2 versions
        results = clientV3.groups().byGroupId(groupId).artifacts().get();
        Assertions.assertEquals(1, results.getCount());
        Assertions.assertEquals(1, results.getArtifacts().size());
        VersionSearchResults vresults = clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId("actual-artifact").versions().get();
        Assertions.assertEquals(2, vresults.getCount());
        Assertions.assertEquals(2, vresults.getVersions().size());
    }

    @Test
    public void testCreateVersionDryRun() throws Exception {
        String groupId = "testCreateVersionDryRun";
        String artifactId = "the-artifact";

        // Create a group
        CreateGroup createGroup = new CreateGroup();
        createGroup.setGroupId(groupId);
        clientV3.groups().post(createGroup);

        // Add a rule
        CreateRule createRule = new CreateRule();
        createRule.setRuleType(RuleType.VALIDITY);
        createRule.setConfig(ValidityLevel.FULL.name());
        clientV3.groups().byGroupId(groupId).rules().post(createRule);

        // Create an artifact
        createArtifact(groupId, artifactId, ArtifactType.AVRO, SCHEMA_SIMPLE, ContentTypes.APPLICATION_JSON);

        // DryRun: try to create an invalid version
        Error error = Assertions.assertThrows(Error.class, () -> {
            CreateVersion createVersion = TestUtils.clientCreateVersion(INVALID_SCHEMA,
                    ContentTypes.APPLICATION_JSON);
            clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                    .post(createVersion, config -> {
                        config.queryParameters.dryRun = true;
                    });
        });
        Assertions.assertEquals("Syntax violation for Avro artifact.", error.getMessageEscaped());

        // DryRun: try to create a valid version (appears to work)
        {
            CreateVersion createVersion = TestUtils.clientCreateVersion(SCHEMA_SIMPLE,
                    ContentTypes.APPLICATION_JSON);
            createVersion.setName("DryRunVersion");
            VersionMetaData vmd = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                    .versions().post(createVersion, config -> {
                        config.queryParameters.dryRun = true;
                    });
            Assertions.assertNotNull(vmd);
            Assertions.assertEquals("DryRunVersion", vmd.getName());
            Assertions.assertEquals("2", vmd.getVersion());
        }

        // Verify that the artifact exists but only has 1 version
        ArtifactSearchResults results = clientV3.groups().byGroupId(groupId).artifacts().get();
        Assertions.assertEquals(1, results.getCount());
        Assertions.assertEquals(1, results.getArtifacts().size());
        VersionSearchResults vresults = clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId(artifactId).versions().get();
        Assertions.assertEquals(1, vresults.getCount());
        Assertions.assertEquals(1, vresults.getVersions().size());
    }

}
