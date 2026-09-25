package io.apicurio.registry.noprofile.maven;

import io.apicurio.registry.maven.DownloadRegistryMojo;
import io.apicurio.registry.maven.RegisterArtifact;
import io.apicurio.registry.maven.RegisterRegistryMojo;
import io.apicurio.registry.rest.client.models.ArtifactReference;
import io.apicurio.registry.rest.client.models.HandleReferencesType;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.rest.v3.beans.IfArtifactExists;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@QuarkusTest
public class RegistryMojoWithAutoReferencesTest extends RegistryMojoTestBase {

    private static final String PROTO_SCHEMA_EXTENSION = ".proto";
    private static final String AVSC_SCHEMA_EXTENSION = ".avsc";
    private static final String JSON_SCHEMA_EXTENSION = ".json";
    private static final String YAML_SCHEMA_EXTENSION = ".yaml";

    RegisterRegistryMojo registerMojo;
    DownloadRegistryMojo downloadMojo;

    @BeforeEach
    public void createMojos() {
        this.registerMojo = new RegisterRegistryMojo();
        this.registerMojo.setRegistryUrl(TestUtils.getRegistryV3ApiUrl(testPort));

        this.downloadMojo = new DownloadRegistryMojo();
        this.downloadMojo.setRegistryUrl(TestUtils.getRegistryV3ApiUrl(testPort));
    }

    @Test
    public void autoRegisterAvroWithReferences() throws Exception {
        String groupId = "autoRegisterAvroWithReferences";
        String artifactId = "tradeRaw";

        File tradeRawFile = new File(getClass().getResource("TradeRawArray.avsc").getFile());

        Set<String> avroFiles = Arrays.stream(Objects.requireNonNull(
                tradeRawFile.getParentFile().listFiles((dir, name) -> name.endsWith(AVSC_SCHEMA_EXTENSION))))
                .map(file -> {
                    FileInputStream fis = null;
                    try {
                        fis = new FileInputStream(file);
                    } catch (FileNotFoundException e) {
                    }
                    return IoUtil.toString(fis);
                }).collect(Collectors.toSet());

        RegisterArtifact tradeRawArtifact = new RegisterArtifact();
        tradeRawArtifact.setGroupId(groupId);
        tradeRawArtifact.setArtifactId(artifactId);
        tradeRawArtifact.setArtifactType(ArtifactType.AVRO);
        tradeRawArtifact.setFile(tradeRawFile);
        tradeRawArtifact.setAutoRefs(true);
        tradeRawArtifact.setIfExists(IfArtifactExists.FAIL);
        tradeRawArtifact.setAvroAutoRefsNamingStrategy(RegisterArtifact.AvroAutoRefsNamingStrategy.INHERIT_PARENT_GROUP);

        registerMojo.setArtifacts(Collections.singletonList(tradeRawArtifact));
        registerMojo.execute();

        // Assertions
        validateStructure(groupId, artifactId, 1, 3, avroFiles);
    }

    @Test
    public void autoRegisterProtoWithReferences() throws Exception {
        // Preparation
        String groupId = "autoRegisterProtoWithReferences";
        String artifactId = "tableNotification";

        File tableNotificationFile = new File(getClass().getResource("sample/table_notification.proto").getFile());

        Set<String> protoFiles = Set.of(
                loadResource("sample/table_info.proto"),
                loadResource("sample/table_notification.proto"),
                loadResource("sample/table_notification_type.proto"),
                loadResource("mode/mode.proto")
        );

        RegisterArtifact tableNotification = new RegisterArtifact();
        tableNotification.setGroupId(groupId);
        tableNotification.setArtifactId(artifactId);
        tableNotification.setArtifactType(ArtifactType.PROTOBUF);
        tableNotification.setFile(tableNotificationFile);
        tableNotification.setAutoRefs(true);
        tableNotification.setIfExists(IfArtifactExists.FAIL);
        // Set the proto-path to be the parent of the "sample" directory, which in this case is
        // our protobuf root directory for our test data.
        tableNotification.setProtoPaths(List.of(tableNotificationFile.getParentFile().getParentFile()));

        registerMojo.setArtifacts(Collections.singletonList(tableNotification));

        // Execution
        registerMojo.execute();

        // Assertions
        validateStructure(groupId, artifactId, 2, 4, protoFiles);
    }

    @Test
    public void autoRegisterJsonSchemaWithReferences() throws Exception {
        // Preparation
        String groupId = "autoRegisterJsonSchemaWithReferences";
        String artifactId = "citizen";

        File citizenFile = new File(getClass().getResource("citizen.json").getFile());

        Set<String> schemaFiles = Set.of(
                loadResource("citizen.json"),
                loadResource("city.json"),
                loadResource("citizenIdentifier.json"),
                loadResource("qualification/qualification.json")
        );

        RegisterArtifact citizen = new RegisterArtifact();
        citizen.setGroupId(groupId);
        citizen.setArtifactId(artifactId);
        citizen.setArtifactType(ArtifactType.JSON);
        citizen.setFile(citizenFile);
        citizen.setAutoRefs(true);
        citizen.setIfExists(IfArtifactExists.FAIL);

        registerMojo.setArtifacts(Collections.singletonList(citizen));

        // Execution
        registerMojo.execute();

        // Assertions
        validateStructure(groupId, artifactId, 3, 4, schemaFiles);
    }

    /**
     * Test that autoRefs works correctly with OpenAPI YAML files.
     * This test addresses issue #6954 - verifies that YAML OpenAPI files
     * are properly parsed and indexed alongside JSON files.
     */
    @Test
    public void autoRegisterOpenApiYamlWithReferences() throws Exception {
        // Preparation
        String groupId = "autoRegisterOpenApiYamlWithReferences";
        String artifactId = "petstore-api";

        File petstoreApiFile = new File(getClass().getResource("openapi-yaml/petstore-api.yaml").getFile());

        Set<String> yamlFiles = Arrays.stream(Objects.requireNonNull(
                petstoreApiFile.getParentFile().listFiles((dir, name) -> name.endsWith(YAML_SCHEMA_EXTENSION))))
                .map(file -> {
                    FileInputStream fis = null;
                    try {
                        fis = new FileInputStream(file);
                    } catch (FileNotFoundException e) {
                    }
                    return IoUtil.toString(fis);
                }).collect(Collectors.toSet());

        RegisterArtifact petstoreApi = new RegisterArtifact();
        petstoreApi.setGroupId(groupId);
        petstoreApi.setArtifactId(artifactId);
        petstoreApi.setArtifactType(ArtifactType.OPENAPI);
        petstoreApi.setFile(petstoreApiFile);
        petstoreApi.setAutoRefs(true);
        petstoreApi.setIfExists(IfArtifactExists.FAIL);

        registerMojo.setArtifacts(Collections.singletonList(petstoreApi));

        // Execution
        registerMojo.execute();

        // Assertions
        // petstore-api.yaml references: Pet.yaml (1 direct reference)
        // Pet.yaml references: Owner.yaml (1 nested reference)
        // Total artifacts: petstore-api.yaml, Pet.yaml, Owner.yaml = 3
        validateStructure(groupId, artifactId, 1, 3, yamlFiles);
    }

    /**
     * Test that autoRefs works correctly with Avro schemas using relative type names.
     * This test addresses issue #6710 - verifies that type references without fully
     * qualified names (e.g., "Customer" instead of "com.example.trade.Customer")
     * are properly resolved using the enclosing namespace.
     */
    @Test
    public void autoRegisterAvroWithRelativeTypeNames() throws Exception {
        String groupId = "autoRegisterAvroWithRelativeTypeNames";
        String artifactId = "tradeOrder";

        File tradeOrderFile = new File(getClass().getResource("autorefs-relative/TradeOrder.avsc").getFile());

        Set<String> avroFiles = Arrays.stream(Objects.requireNonNull(
                tradeOrderFile.getParentFile().listFiles((dir, name) -> name.endsWith(AVSC_SCHEMA_EXTENSION))))
                .map(file -> {
                    FileInputStream fis = null;
                    try {
                        fis = new FileInputStream(file);
                    } catch (FileNotFoundException e) {
                    }
                    return IoUtil.toString(fis);
                }).collect(Collectors.toSet());

        RegisterArtifact tradeOrderArtifact = new RegisterArtifact();
        tradeOrderArtifact.setGroupId(groupId);
        tradeOrderArtifact.setArtifactId(artifactId);
        tradeOrderArtifact.setArtifactType(ArtifactType.AVRO);
        tradeOrderArtifact.setFile(tradeOrderFile);
        tradeOrderArtifact.setAutoRefs(true);
        tradeOrderArtifact.setAvroAutoRefsNamingStrategy(RegisterArtifact.AvroAutoRefsNamingStrategy.INHERIT_PARENT_GROUP);
        tradeOrderArtifact.setIfExists(IfArtifactExists.FAIL);

        registerMojo.setArtifacts(Collections.singletonList(tradeOrderArtifact));
        registerMojo.execute();

        // Assertions
        // TradeOrder references: Customer, Instrument (2 direct references)
        // Customer references: Address (1 nested reference)
        // Total artifacts: TradeOrder, Customer, Address, Instrument = 4
        validateStructure(groupId, artifactId, 2, 4, avroFiles);

        // Verify that the references are correctly registered
        final VersionMetaData tradeOrderMetadata = clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId(artifactId).versions().byVersionExpression("branch=latest").get();
        final List<ArtifactReference> tradeOrderReferences = clientV3.ids().globalIds()
                .byGlobalId(tradeOrderMetadata.getGlobalId()).references().get();

        // Verify the reference names are fully qualified
        Set<String> referenceNames = tradeOrderReferences.stream()
                .map(ArtifactReference::getName)
                .collect(Collectors.toSet());
        Assertions.assertTrue(referenceNames.contains("com.example.trade.Customer"));
        Assertions.assertTrue(referenceNames.contains("com.example.trade.Instrument"));
    }

    @Test
    public void autoRegisterAvroWithOptionalReferences() throws Exception {
        String groupId = "autoRegisterAvroWithOptionalReferences";
        String artifactId = "trade";

        File tradeFile = new File(getClass().getResource("Trade.avsc").getFile());

        Set<String> avroFiles = Arrays.stream(Objects.requireNonNull(
                tradeFile.getParentFile().listFiles((dir, name) -> name.endsWith(AVSC_SCHEMA_EXTENSION))))
                .map(file -> {
                    FileInputStream fis = null;
                    try {
                        fis = new FileInputStream(file);
                    } catch (FileNotFoundException e) {
                    }
                    return IoUtil.toString(fis);
                }).collect(Collectors.toSet());

        RegisterArtifact tradeArtifact = new RegisterArtifact();
        tradeArtifact.setGroupId(groupId);
        tradeArtifact.setArtifactId(artifactId);
        tradeArtifact.setArtifactType(ArtifactType.AVRO);
        tradeArtifact.setFile(tradeFile);
        tradeArtifact.setAutoRefs(true);
        tradeArtifact.setIfExists(IfArtifactExists.FAIL);
        tradeArtifact.setAvroAutoRefsNamingStrategy(RegisterArtifact.AvroAutoRefsNamingStrategy.INHERIT_PARENT_GROUP);

        registerMojo.setArtifacts(Collections.singletonList(tradeArtifact));
        registerMojo.execute();

        // Assertions - Trade schema has 2 optional references (Price and Quantity)
        final VersionMetaData artifactWithReferences = clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId(artifactId).versions().byVersionExpression("branch=latest").get();

        final List<ArtifactReference> mainArtifactReferences = clientV3.ids().globalIds()
                .byGlobalId(artifactWithReferences.getGlobalId()).references().get();

        // The main artifact should have 2 references (Price and Quantity)
        Assertions.assertEquals(2, mainArtifactReferences.size(), "The main artifact should have 2 references");

        // Verify both references are present
        Set<String> referenceNames = mainArtifactReferences.stream()
                .map(ArtifactReference::getName)
                .collect(Collectors.toSet());
        Assertions.assertTrue(referenceNames.contains("io.apicurio.test.trade.Price"));
        Assertions.assertTrue(referenceNames.contains("io.apicurio.test.trade.Quantity"));
    }

    @Test
    public void autoRegisterJsonSchemaWithReferencesDeref() throws Exception {
        // Preparation
        String groupId = "autoRegisterJsonSchemaWithReferencesDeref";
        String artifactId = "stock";
        String version = "1.0.0";

        File stockFile = new File(getClass().getResource("./stock/FLIStockAdjustment.json").getFile());

        Set<String> jsonFiles = Arrays.stream(Objects.requireNonNull(
                stockFile.getParentFile().listFiles((dir, name) -> name.endsWith(JSON_SCHEMA_EXTENSION))))
                .map(file -> {
                    FileInputStream fis = null;
                    try {
                        fis = new FileInputStream(file);
                    } catch (FileNotFoundException e) {
                    }
                    return IoUtil.toString(fis);
                }).collect(Collectors.toSet());

        RegisterArtifact stock = new RegisterArtifact();
        stock.setAutoRefs(true);
        stock.setGroupId(groupId);
        stock.setArtifactId(artifactId);
        stock.setVersion(version);
        stock.setArtifactType(ArtifactType.JSON);
        stock.setFile(stockFile);
        stock.setIfExists(IfArtifactExists.FIND_OR_CREATE_VERSION);

        registerMojo.setArtifacts(Collections.singletonList(stock));

        // Execution
        registerMojo.execute();

        // Assertions
        validateStructure(groupId, artifactId, 9, 6, jsonFiles);

        final VersionMetaData artifactWithReferences = clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId(artifactId).versions().byVersionExpression(version).get();
        InputStream contentByGlobalId = clientV3.ids().globalIds()
                .byGlobalId(artifactWithReferences.getGlobalId()).get(getRequestConfiguration -> {
                    getRequestConfiguration.queryParameters.references = HandleReferencesType.DEREFERENCE;
                });

        File stockFileDeref = new File(
                getClass().getResource("./stock/FLIStockAdjustment_deref.json").getFile());

        FileInputStream fis = null;
        try {
            fis = new FileInputStream(stockFileDeref);
        } catch (FileNotFoundException e) {
        }

        Assertions.assertEquals(IoUtil.toString(fis), IoUtil.toString(contentByGlobalId));
    }

    private void validateStructure(String groupId, String artifactId, int expectedMainReferences,
            int expectedTotalArtifacts, Set<String> originalContents) throws Exception {
        final VersionMetaData artifactWithReferences = clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId(artifactId).versions().byVersionExpression("branch=latest").get();
        final String mainContent = new String(clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId(artifactId).versions().byVersionExpression(artifactWithReferences.getVersion())
                .content().get().readAllBytes(), StandardCharsets.UTF_8);

        Assertions.assertTrue(originalContents.contains(mainContent), "The main content should have been registered as-is"); // The main content has been registered as-is.

        final List<ArtifactReference> mainArtifactReferences = clientV3.ids().globalIds()
                .byGlobalId(artifactWithReferences.getGlobalId()).references().get();

        // The main artifact has the expected number of references
        Assertions.assertEquals(expectedMainReferences, mainArtifactReferences.size(), "The main artifact does not have the expected number of references");

        // Validate all the contents are registered as they are in the file system.
        validateReferences(mainArtifactReferences, originalContents);

        // The total number of artifacts for the directory structure is the expected.
        Assertions.assertEquals(expectedTotalArtifacts,
                clientV3.groups().byGroupId(groupId).artifacts().get().getCount().intValue(),
                "The total number of artifacts for the directory structure is incorrect.");
    }

    private void validateReferences(List<ArtifactReference> artifactReferences, Set<String> loadedContents)
            throws Exception {
        for (ArtifactReference artifactReference : artifactReferences) {
            String referenceContent = new String(clientV3.groups().byGroupId(artifactReference.getGroupId())
                    .artifacts().byArtifactId(artifactReference.getArtifactId()).versions()
                    .byVersionExpression(artifactReference.getVersion()).content().get().readAllBytes(),
                    StandardCharsets.UTF_8);
            VersionMetaData referenceMetadata = clientV3.groups().byGroupId(artifactReference.getGroupId())
                    .artifacts().byArtifactId(artifactReference.getArtifactId()).versions()
                    .byVersionExpression("branch=latest").get();

            Assertions.assertTrue(loadedContents.contains(referenceContent),
                    String.format("Reference content not found in loaded contents. GroupId: %s, ArtifactId: %s, Version: %s",
                            artifactReference.getGroupId(), artifactReference.getArtifactId(), artifactReference.getVersion()));

            List<ArtifactReference> nestedReferences = clientV3.ids().globalIds()
                    .byGlobalId(referenceMetadata.getGlobalId()).references().get();

            if (!nestedReferences.isEmpty()) {
                validateReferences(nestedReferences, loadedContents);
            }
        }
    }

    private String loadResource(String protoFilePath) {
        try (InputStream resourceStream = getClass().getResourceAsStream(protoFilePath)) {
            return IoUtil.toString(resourceStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}