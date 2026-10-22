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

    private static final String SCHEMA_DEBEZIUM_ENUM_V1 = """
            {
                "type": "record",
                "name": "Value",
                "namespace": "com.example.dbserver1.public.shipments",
                "fields": [
                    {
                        "name": "id",
                        "type": "int"
                    },
                    {
                        "name": "status",
                        "type": [
                            "null",
                            {
                                "type": "string",
                                "connect.parameters": {
                                    "allowed": "station,post_office"
                                },
                                "connect.name": "io.debezium.data.Enum",
                                "connect.version": 1
                            }
                        ],
                        "default": null
                    }
                ]
            }
            """;
    private static final String SCHEMA_DEBEZIUM_ENUM_V2 = """
            {
                "type": "record",
                "name": "Value",
                "namespace": "com.example.dbserver1.public.shipments",
                "fields": [
                    {
                        "name": "id",
                        "type": "int"
                    },
                    {
                        "name": "status",
                        "type": [
                            "null",
                            {
                                "type": "string",
                                "connect.parameters": {
                                    "allowed": "station,post_office,plane"
                                },
                                "connect.name": "io.debezium.data.Enum",
                                "connect.version": 1
                            }
                        ],
                        "default": null
                    }
                ]
            }
            """;

    private static final String SCHEMA_DEFAULT_VALUE_V1 = """
            {
                "type": "record",
                "name": "Value",
                "namespace": "com.example.dbserver1.public.config",
                "fields": [
                    {
                        "name": "id",
                        "type": "int"
                    },
                    {
                        "name": "shard",
                        "type": "int",
                        "default": 0
                    }
                ]
            }
            """;
    private static final String SCHEMA_DEFAULT_VALUE_V2 = """
            {
                "type": "record",
                "name": "Value",
                "namespace": "com.example.dbserver1.public.config",
                "fields": [
                    {
                        "name": "id",
                        "type": "int"
                    },
                    {
                        "name": "shard",
                        "type": "int",
                        "default": 1
                    }
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
        createArtifact.getFirstVersion().setVersion("1.0");
        CreateArtifactResponse car = clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);
        Assertions.assertNotNull(car);
        Assertions.assertEquals(groupId, car.getArtifact().getGroupId());
        Assertions.assertEquals(artifactId, car.getArtifact().getArtifactId());
        Assertions.assertEquals(ArtifactType.AVRO, car.getArtifact().getArtifactType());
        Assertions.assertEquals("1.0", car.getVersion().getVersion());

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
        createArtifact.getFirstVersion().setVersion("1.0");
        CreateArtifactResponse car = clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);
        Assertions.assertNotNull(car);
        Assertions.assertEquals(groupId, car.getArtifact().getGroupId());
        Assertions.assertEquals(artifactId, car.getArtifact().getArtifactId());
        Assertions.assertEquals(ArtifactType.AVRO, car.getArtifact().getArtifactType());
        Assertions.assertEquals("1.0", car.getVersion().getVersion());

        // Create the same exact thing again, with ifExists=FIND_OR_CREATE_VERSION
        CreateArtifact ca = TestUtils.clientCreateArtifact(artifactId, ArtifactType.AVRO,
                SCHEMA_SIMPLE, ContentTypes.APPLICATION_JSON);
        ca.getFirstVersion().setVersion("1.0");
        car = clientV3.groups().byGroupId(groupId).artifacts().post(ca,
                config -> config.queryParameters.ifExists = IfArtifactExists.FIND_OR_CREATE_VERSION);
        Assertions.assertNotNull(car);
        Assertions.assertEquals(groupId, car.getArtifact().getGroupId());
        Assertions.assertEquals(artifactId, car.getArtifact().getArtifactId());
        Assertions.assertEquals(ArtifactType.AVRO, car.getArtifact().getArtifactType());
        Assertions.assertEquals("1.0", car.getVersion().getVersion());

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

    /**
     * Reproducer for Debezium regression: when only connect.parameters values change
     * (e.g. io.debezium.data.Enum "allowed" values), FIND_OR_CREATE_VERSION must create
     * a new version, not return the existing one.
     */
    @Test
    public void testIfExistsFindOrCreateVersion_DifferentConnectParameters() throws Exception {
        String groupId = "testIfExistsFindOrCreateVersion_DifferentConnectParameters";
        String artifactId = "shipments-value";

        CreateGroup createGroup = new CreateGroup();
        createGroup.setGroupId(groupId);
        clientV3.groups().post(createGroup);

        // Register v1 with allowed=station,post_office
        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, ArtifactType.AVRO,
                SCHEMA_DEBEZIUM_ENUM_V1, ContentTypes.APPLICATION_JSON);
        CreateArtifactResponse car = clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);
        Assertions.assertNotNull(car);
        Assertions.assertEquals("1", car.getVersion().getVersion());

        // Register v2 with allowed=station,post_office,plane using FIND_OR_CREATE_VERSION
        CreateArtifact ca = TestUtils.clientCreateArtifact(artifactId, ArtifactType.AVRO,
                SCHEMA_DEBEZIUM_ENUM_V2, ContentTypes.APPLICATION_JSON);
        car = clientV3.groups().byGroupId(groupId).artifacts().post(ca,
                config -> config.queryParameters.ifExists = IfArtifactExists.FIND_OR_CREATE_VERSION);
        Assertions.assertNotNull(car);
        Assertions.assertEquals("2", car.getVersion().getVersion(),
                "A new version should be created when connect.parameters values change");

        // Should have two versions
        VersionSearchResults vsr = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().get();
        Assertions.assertEquals(2, vsr.getCount().intValue());
    }

    /**
     * Reproducer for Debezium regression: same as above but with canonical=true.
     * Even with canonical hashing, different connect.parameters must produce different versions.
     */
    @Test
    public void testIfExistsFindOrCreateVersion_DifferentConnectParameters_Canonical() throws Exception {
        String groupId = "testIfExistsFindOrCreateVersion_DifferentConnectParameters_Canonical";
        String artifactId = "shipments-value";

        CreateGroup createGroup = new CreateGroup();
        createGroup.setGroupId(groupId);
        clientV3.groups().post(createGroup);

        // Register v1 with allowed=station,post_office
        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, ArtifactType.AVRO,
                SCHEMA_DEBEZIUM_ENUM_V1, ContentTypes.APPLICATION_JSON);
        CreateArtifactResponse car = clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);
        Assertions.assertNotNull(car);
        Assertions.assertEquals("1", car.getVersion().getVersion());

        // Register v2 with allowed=station,post_office,plane using FIND_OR_CREATE_VERSION + canonical
        CreateArtifact ca = TestUtils.clientCreateArtifact(artifactId, ArtifactType.AVRO,
                SCHEMA_DEBEZIUM_ENUM_V2, ContentTypes.APPLICATION_JSON);
        car = clientV3.groups().byGroupId(groupId).artifacts().post(ca,
                config -> {
                    config.queryParameters.ifExists = IfArtifactExists.FIND_OR_CREATE_VERSION;
                    config.queryParameters.canonical = true;
                });
        Assertions.assertNotNull(car);
        Assertions.assertEquals("2", car.getVersion().getVersion(),
                "A new version should be created when connect.parameters values change (canonical mode)");

        // Should have two versions
        VersionSearchResults vsr = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().get();
        Assertions.assertEquals(2, vsr.getCount().intValue());
    }

    /**
     * Reproducer for Debezium regression: when only a field's default value changes,
     * FIND_OR_CREATE_VERSION must create a new version.
     */
    @Test
    public void testIfExistsFindOrCreateVersion_DifferentDefaultValues() throws Exception {
        String groupId = "testIfExistsFindOrCreateVersion_DifferentDefaultValues";
        String artifactId = "config-value";

        CreateGroup createGroup = new CreateGroup();
        createGroup.setGroupId(groupId);
        clientV3.groups().post(createGroup);

        // Register v1 with default=0
        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, ArtifactType.AVRO,
                SCHEMA_DEFAULT_VALUE_V1, ContentTypes.APPLICATION_JSON);
        CreateArtifactResponse car = clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);
        Assertions.assertNotNull(car);
        Assertions.assertEquals("1", car.getVersion().getVersion());

        // Register v2 with default=1 using FIND_OR_CREATE_VERSION
        CreateArtifact ca = TestUtils.clientCreateArtifact(artifactId, ArtifactType.AVRO,
                SCHEMA_DEFAULT_VALUE_V2, ContentTypes.APPLICATION_JSON);
        car = clientV3.groups().byGroupId(groupId).artifacts().post(ca,
                config -> config.queryParameters.ifExists = IfArtifactExists.FIND_OR_CREATE_VERSION);
        Assertions.assertNotNull(car);
        Assertions.assertEquals("2", car.getVersion().getVersion(),
                "A new version should be created when field default values change");

        // Should have two versions
        VersionSearchResults vsr = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().get();
        Assertions.assertEquals(2, vsr.getCount().intValue());
    }

    /**
     * Reproducer for Debezium regression: same as above but with canonical=true.
     * Even with canonical hashing, different default values must produce different versions.
     */
    @Test
    public void testIfExistsFindOrCreateVersion_DifferentDefaultValues_Canonical() throws Exception {
        String groupId = "testIfExistsFindOrCreateVersion_DifferentDefaultValues_Canonical";
        String artifactId = "config-value";

        CreateGroup createGroup = new CreateGroup();
        createGroup.setGroupId(groupId);
        clientV3.groups().post(createGroup);

        // Register v1 with default=0
        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, ArtifactType.AVRO,
                SCHEMA_DEFAULT_VALUE_V1, ContentTypes.APPLICATION_JSON);
        CreateArtifactResponse car = clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);
        Assertions.assertNotNull(car);
        Assertions.assertEquals("1", car.getVersion().getVersion());

        // Register v2 with default=1 using FIND_OR_CREATE_VERSION + canonical
        CreateArtifact ca = TestUtils.clientCreateArtifact(artifactId, ArtifactType.AVRO,
                SCHEMA_DEFAULT_VALUE_V2, ContentTypes.APPLICATION_JSON);
        car = clientV3.groups().byGroupId(groupId).artifacts().post(ca,
                config -> {
                    config.queryParameters.ifExists = IfArtifactExists.FIND_OR_CREATE_VERSION;
                    config.queryParameters.canonical = true;
                });
        Assertions.assertNotNull(car);
        Assertions.assertEquals("2", car.getVersion().getVersion(),
                "A new version should be created when field default values change (canonical mode)");

        // Should have two versions
        VersionSearchResults vsr = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().get();
        Assertions.assertEquals(2, vsr.getCount().intValue());
    }

}
