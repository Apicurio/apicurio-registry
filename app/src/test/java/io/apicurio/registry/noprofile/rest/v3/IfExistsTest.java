package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateArtifactResponse;
import io.apicurio.registry.rest.client.models.CreateGroup;
import io.apicurio.registry.rest.client.models.IfArtifactExists;
import io.apicurio.registry.rest.client.models.VersionSearchResults;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.MutabilityEnabledProfile;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(MutabilityEnabledProfile.class)
public class IfExistsTest extends AbstractResourceTestBase {

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
    private static final String SCHEMA_SIMPLE_MIN = """
            {"type":"record","namespace":"com.example","name":"FullName","fields":[{"name":"first","type":"string"},{"name":"last","type":"string"}]}
            """;
    private static final String SCHEMA_SIMPLE_V2 = """
            {
                 "type": "record",
                 "namespace": "com.example",
                 "name": "FullName",
                 "fields": [
                   { "name": "first", "type": "string" },
                   { "name": "middle", "type": "string" },
                   { "name": "last", "type": "string" }
                 ]
            }
            """;

    @Test
    public void testIfExistsFail() throws Exception {
        String groupId = "testIfExistsFail";
        String artifactId = "valid-artifact";

        // Create a group
        CreateGroup createGroup = new CreateGroup();
        createGroup.setGroupId(groupId);
        clientV3.groups().post(createGroup);

        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, ArtifactType.AVRO,
                SCHEMA_SIMPLE, ContentTypes.APPLICATION_JSON);
        CreateArtifactResponse car = clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);
        Assertions.assertNotNull(car);
        Assertions.assertEquals(groupId, car.getArtifact().getGroupId());
        Assertions.assertEquals(artifactId, car.getArtifact().getArtifactId());
        Assertions.assertEquals(ArtifactType.AVRO, car.getArtifact().getArtifactType());
        Assertions.assertEquals("1", car.getVersion().getVersion());

        // Create the same thing again, with ifExists=FAIL
        Assertions.assertThrows(Exception.class, () -> {
            CreateArtifact ca = TestUtils.clientCreateArtifact(artifactId, ArtifactType.AVRO,
                    SCHEMA_SIMPLE, ContentTypes.APPLICATION_JSON);
            clientV3.groups().byGroupId(groupId).artifacts().post(ca,
                    config -> config.queryParameters.ifExists = IfArtifactExists.FAIL);
        });

        // Should only have one version
        VersionSearchResults vsr = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().get();
        Assertions.assertNotNull(vsr);
        Assertions.assertEquals(1, vsr.getVersions().size());
        Assertions.assertEquals(1, vsr.getCount().intValue());
    }

    @Test
    public void testIfExistsCreateVersion_Identical() throws Exception {
        String groupId = "testIfExistsCreateVersion_Identical";
        String artifactId = "valid-artifact";

        // Create a group
        CreateGroup createGroup = new CreateGroup();
        createGroup.setGroupId(groupId);
        clientV3.groups().post(createGroup);

        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, ArtifactType.AVRO,
                SCHEMA_SIMPLE, ContentTypes.APPLICATION_JSON);
        CreateArtifactResponse car = clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);
        Assertions.assertNotNull(car);
        Assertions.assertEquals(groupId, car.getArtifact().getGroupId());
        Assertions.assertEquals(artifactId, car.getArtifact().getArtifactId());
        Assertions.assertEquals(ArtifactType.AVRO, car.getArtifact().getArtifactType());
        Assertions.assertEquals("1", car.getVersion().getVersion());

        // Create the same thing again, with ifExists=CREATE_VERSION
        CreateArtifact ca = TestUtils.clientCreateArtifact(artifactId, ArtifactType.AVRO,
                SCHEMA_SIMPLE, ContentTypes.APPLICATION_JSON);
        car = clientV3.groups().byGroupId(groupId).artifacts().post(ca,
                config -> config.queryParameters.ifExists = IfArtifactExists.CREATE_VERSION);
        Assertions.assertNotNull(car);
        Assertions.assertEquals(groupId, car.getArtifact().getGroupId());
        Assertions.assertEquals(artifactId, car.getArtifact().getArtifactId());
        Assertions.assertEquals(ArtifactType.AVRO, car.getArtifact().getArtifactType());
        Assertions.assertEquals("2", car.getVersion().getVersion());

        // Should have two versions now
        VersionSearchResults vsr = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().get();
        Assertions.assertNotNull(vsr);
        Assertions.assertEquals(2, vsr.getVersions().size());
        Assertions.assertEquals(2, vsr.getCount().intValue());
    }

    @Test
    public void testIfExistsCreateVersion_Equivalent() throws Exception {
        String groupId = "testIfExistsCreateVersion_Equivalent";
        String artifactId = "valid-artifact";

        // Create a group
        CreateGroup createGroup = new CreateGroup();
        createGroup.setGroupId(groupId);
        clientV3.groups().post(createGroup);

        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, ArtifactType.AVRO,
                SCHEMA_SIMPLE, ContentTypes.APPLICATION_JSON);
        CreateArtifactResponse car = clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);
        Assertions.assertNotNull(car);
        Assertions.assertEquals(groupId, car.getArtifact().getGroupId());
        Assertions.assertEquals(artifactId, car.getArtifact().getArtifactId());
        Assertions.assertEquals(ArtifactType.AVRO, car.getArtifact().getArtifactType());
        Assertions.assertEquals("1", car.getVersion().getVersion());

        // Create the same thing again, with ifExists=CREATE_VERSION
        CreateArtifact ca = TestUtils.clientCreateArtifact(artifactId, ArtifactType.AVRO,
                SCHEMA_SIMPLE_MIN, ContentTypes.APPLICATION_JSON);
        car = clientV3.groups().byGroupId(groupId).artifacts().post(ca,
                config -> config.queryParameters.ifExists = IfArtifactExists.CREATE_VERSION);
        Assertions.assertNotNull(car);
        Assertions.assertEquals(groupId, car.getArtifact().getGroupId());
        Assertions.assertEquals(artifactId, car.getArtifact().getArtifactId());
        Assertions.assertEquals(ArtifactType.AVRO, car.getArtifact().getArtifactType());
        Assertions.assertEquals("2", car.getVersion().getVersion());

        // Should have two versions now
        VersionSearchResults vsr = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().get();
        Assertions.assertNotNull(vsr);
        Assertions.assertEquals(2, vsr.getVersions().size());
        Assertions.assertEquals(2, vsr.getCount().intValue());
    }

    @Test
    public void testIfExistsCreateVersion_New() throws Exception {
        String groupId = "testIfExistsCreateVersion_New";
        String artifactId = "valid-artifact";

        // Create a group
        CreateGroup createGroup = new CreateGroup();
        createGroup.setGroupId(groupId);
        clientV3.groups().post(createGroup);

        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, ArtifactType.AVRO,
                SCHEMA_SIMPLE, ContentTypes.APPLICATION_JSON);
        CreateArtifactResponse car = clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);
        Assertions.assertNotNull(car);
        Assertions.assertEquals(groupId, car.getArtifact().getGroupId());
        Assertions.assertEquals(artifactId, car.getArtifact().getArtifactId());
        Assertions.assertEquals(ArtifactType.AVRO, car.getArtifact().getArtifactType());
        Assertions.assertEquals("1", car.getVersion().getVersion());

        // Create the same artifact again (but with a new version), with ifExists=CREATE_VERSION
        CreateArtifact ca = TestUtils.clientCreateArtifact(artifactId, ArtifactType.AVRO,
                SCHEMA_SIMPLE_V2, ContentTypes.APPLICATION_JSON);
        car = clientV3.groups().byGroupId(groupId).artifacts().post(ca,
                config -> config.queryParameters.ifExists = IfArtifactExists.CREATE_VERSION);
        Assertions.assertNotNull(car);
        Assertions.assertEquals(groupId, car.getArtifact().getGroupId());
        Assertions.assertEquals(artifactId, car.getArtifact().getArtifactId());
        Assertions.assertEquals(ArtifactType.AVRO, car.getArtifact().getArtifactType());
        Assertions.assertEquals("2", car.getVersion().getVersion());

        // Should have two versions now
        VersionSearchResults vsr = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().get();
        Assertions.assertNotNull(vsr);
        Assertions.assertEquals(2, vsr.getVersions().size());
        Assertions.assertEquals(2, vsr.getCount().intValue());
    }

    @Test
    public void testIfExistsFindOrCreateVersion_Identical() throws Exception {
        String groupId = "testIfExistsFindOrCreateVersion_Identical";
        String artifactId = "valid-artifact";

        // Create a group
        CreateGroup createGroup = new CreateGroup();
        createGroup.setGroupId(groupId);
        clientV3.groups().post(createGroup);

        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, ArtifactType.AVRO,
                SCHEMA_SIMPLE, ContentTypes.APPLICATION_JSON);
        CreateArtifactResponse car = clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);
        Assertions.assertNotNull(car);
        Assertions.assertEquals(groupId, car.getArtifact().getGroupId());
        Assertions.assertEquals(artifactId, car.getArtifact().getArtifactId());
        Assertions.assertEquals(ArtifactType.AVRO, car.getArtifact().getArtifactType());
        Assertions.assertEquals("1", car.getVersion().getVersion());

        // Create the same exact thing again, with ifExists=FIND_OR_CREATE_VERSION
        CreateArtifact ca = TestUtils.clientCreateArtifact(artifactId, ArtifactType.AVRO,
                SCHEMA_SIMPLE, ContentTypes.APPLICATION_JSON);
        car = clientV3.groups().byGroupId(groupId).artifacts().post(ca,
                config -> config.queryParameters.ifExists = IfArtifactExists.FIND_OR_CREATE_VERSION);
        Assertions.assertNotNull(car);
        Assertions.assertEquals(groupId, car.getArtifact().getGroupId());
        Assertions.assertEquals(artifactId, car.getArtifact().getArtifactId());
        Assertions.assertEquals(ArtifactType.AVRO, car.getArtifact().getArtifactType());
        Assertions.assertEquals("1", car.getVersion().getVersion());

        // Should have only one version
        VersionSearchResults vsr = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().get();
        Assertions.assertNotNull(vsr);
        Assertions.assertEquals(1, vsr.getVersions().size());
        Assertions.assertEquals(1, vsr.getCount().intValue());
    }

    @Test
    public void testIfExistsFindOrCreateVersion_New() throws Exception {
        String groupId = "testIfExistsFindOrCreateVersion_New";
        String artifactId = "valid-artifact";

        // Create a group
        CreateGroup createGroup = new CreateGroup();
        createGroup.setGroupId(groupId);
        clientV3.groups().post(createGroup);

        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, ArtifactType.AVRO,
                SCHEMA_SIMPLE, ContentTypes.APPLICATION_JSON);
        CreateArtifactResponse car = clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);
        Assertions.assertNotNull(car);
        Assertions.assertEquals(groupId, car.getArtifact().getGroupId());
        Assertions.assertEquals(artifactId, car.getArtifact().getArtifactId());
        Assertions.assertEquals(ArtifactType.AVRO, car.getArtifact().getArtifactType());
        Assertions.assertEquals("1", car.getVersion().getVersion());

        // Create the same exact thing again, with ifExists=FIND_OR_CREATE_VERSION
        CreateArtifact ca = TestUtils.clientCreateArtifact(artifactId, ArtifactType.AVRO,
                SCHEMA_SIMPLE_V2, ContentTypes.APPLICATION_JSON);
        car = clientV3.groups().byGroupId(groupId).artifacts().post(ca,
                config -> config.queryParameters.ifExists = IfArtifactExists.FIND_OR_CREATE_VERSION);
        Assertions.assertNotNull(car);
        Assertions.assertEquals(groupId, car.getArtifact().getGroupId());
        Assertions.assertEquals(artifactId, car.getArtifact().getArtifactId());
        Assertions.assertEquals(ArtifactType.AVRO, car.getArtifact().getArtifactType());
        Assertions.assertEquals("2", car.getVersion().getVersion());

        // Should have only one version
        VersionSearchResults vsr = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().get();
        Assertions.assertNotNull(vsr);
        Assertions.assertEquals(2, vsr.getVersions().size());
        Assertions.assertEquals(2, vsr.getCount().intValue());
    }

    @Test
    public void testIfExistsFindOrCreateVersion_Equivalent() throws Exception {
        String groupId = "testIfExistsFindOrCreateVersion_Equivalent";
        String artifactId = "valid-artifact";

        // Create a group
        CreateGroup createGroup = new CreateGroup();
        createGroup.setGroupId(groupId);
        clientV3.groups().post(createGroup);

        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, ArtifactType.AVRO,
                SCHEMA_SIMPLE, ContentTypes.APPLICATION_JSON);
        CreateArtifactResponse car = clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);
        Assertions.assertNotNull(car);
        Assertions.assertEquals(groupId, car.getArtifact().getGroupId());
        Assertions.assertEquals(artifactId, car.getArtifact().getArtifactId());
        Assertions.assertEquals(ArtifactType.AVRO, car.getArtifact().getArtifactType());
        Assertions.assertEquals("1", car.getVersion().getVersion());

        // Create the same exact thing again, with ifExists=FIND_OR_CREATE_VERSION
        CreateArtifact ca = TestUtils.clientCreateArtifact(artifactId, ArtifactType.AVRO,
                SCHEMA_SIMPLE_MIN, ContentTypes.APPLICATION_JSON);
        car = clientV3.groups().byGroupId(groupId).artifacts().post(ca,
                config -> config.queryParameters.ifExists = IfArtifactExists.FIND_OR_CREATE_VERSION);
        Assertions.assertNotNull(car);
        Assertions.assertEquals(groupId, car.getArtifact().getGroupId());
        Assertions.assertEquals(artifactId, car.getArtifact().getArtifactId());
        Assertions.assertEquals(ArtifactType.AVRO, car.getArtifact().getArtifactType());
        Assertions.assertEquals("2", car.getVersion().getVersion());

        // Should have only one version
        VersionSearchResults vsr = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().get();
        Assertions.assertNotNull(vsr);
        Assertions.assertEquals(2, vsr.getVersions().size());
        Assertions.assertEquals(2, vsr.getCount().intValue());
    }

    @Test
    public void testIfExistsFindOrCreateVersion_Equivalent_Canonical() throws Exception {
        String groupId = "testIfExistsFindOrCreateVersion_Equivalent_Canonical";
        String artifactId = "valid-artifact";

        // Create a group
        CreateGroup createGroup = new CreateGroup();
        createGroup.setGroupId(groupId);
        clientV3.groups().post(createGroup);

        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, ArtifactType.AVRO,
                SCHEMA_SIMPLE, ContentTypes.APPLICATION_JSON);
        CreateArtifactResponse car = clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);
        Assertions.assertNotNull(car);
        Assertions.assertEquals(groupId, car.getArtifact().getGroupId());
        Assertions.assertEquals(artifactId, car.getArtifact().getArtifactId());
        Assertions.assertEquals(ArtifactType.AVRO, car.getArtifact().getArtifactType());
        Assertions.assertEquals("1", car.getVersion().getVersion());

        // Create the same exact thing again, with ifExists=FIND_OR_CREATE_VERSION
        CreateArtifact ca = TestUtils.clientCreateArtifact(artifactId, ArtifactType.AVRO,
                SCHEMA_SIMPLE_MIN, ContentTypes.APPLICATION_JSON);
        car = clientV3.groups().byGroupId(groupId).artifacts().post(ca,
                config -> {
                    config.queryParameters.ifExists = IfArtifactExists.FIND_OR_CREATE_VERSION;
                    config.queryParameters.canonical = true;
                });
        Assertions.assertNotNull(car);
        Assertions.assertEquals(groupId, car.getArtifact().getGroupId());
        Assertions.assertEquals(artifactId, car.getArtifact().getArtifactId());
        Assertions.assertEquals(ArtifactType.AVRO, car.getArtifact().getArtifactType());
        Assertions.assertEquals("1", car.getVersion().getVersion());

        // Should have only one version
        VersionSearchResults vsr = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().get();
        Assertions.assertNotNull(vsr);
        Assertions.assertEquals(1, vsr.getVersions().size());
        Assertions.assertEquals(1, vsr.getCount().intValue());
    }

}
