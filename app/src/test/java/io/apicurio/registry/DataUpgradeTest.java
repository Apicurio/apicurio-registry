package io.apicurio.registry;

import io.apicurio.registry.model.GroupId;
import io.apicurio.registry.rest.client.models.ArtifactReference;
import io.apicurio.registry.rest.client.models.ArtifactSearchResults;
import io.apicurio.registry.rest.client.models.BranchMetaData;
import io.apicurio.registry.rest.client.models.HandleReferencesType;
import io.apicurio.registry.rest.client.models.ProblemDetails;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.types.Current;
import io.apicurio.registry.utils.IoUtil;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.apache.avro.Schema;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.anything;

@QuarkusTest
public class DataUpgradeTest extends AbstractResourceTestBase {

    @Inject
    @Current
    RegistryStorage storage;

    @Override
    @BeforeAll
    protected void beforeAll() throws Exception {
        super.beforeAll();
        storage.deleteAllUserData();

        setupRestAssured();

        try (InputStream data = resourceToInputStream("./upgrade/v2_export.zip")) {
            given().when().contentType("application/zip").body(data).post("/registry/v2/admin/import").then()
                    .statusCode(204).body(anything());
        }
    }

    @BeforeEach
    public void beforeEach() {
        setupRestAssured();
    }

    @Test
    public void testCheckGlobalRules() throws Exception {
        // Global rules are enabled in the export file, they must be activated.
        Assertions.assertEquals(3, clientV3.admin().rules().get().size());
        Assertions.assertEquals("SYNTAX_ONLY",
                clientV3.admin().rules().byRuleType("VALIDITY").get().getConfig());
        Assertions.assertEquals("FULL",
                clientV3.admin().rules().byRuleType("COMPATIBILITY").get().getConfig());
        Assertions.assertEquals("NONE", clientV3.admin().rules().byRuleType("INTEGRITY").get().getConfig());
    }

    @Test
    public void testArtifactsCount() {
        Assertions.assertEquals(32, clientV3.search().artifacts().get().getCount());
    }

    @Test
    public void testCheckAvroWithReferences() throws Exception {
        String tradeRawDereferenced = IoUtil.toString(clientV3.groups()
                .byGroupId("avro-maven-with-references-auto").artifacts().byArtifactId("TradeRaw").versions()
                .byVersionExpression("2.0").content().get(configuration -> {
                    configuration.queryParameters.references = HandleReferencesType.DEREFERENCE;
                }));

        Assertions.assertEquals(
                "{\"type\":\"record\",\"name\":\"TradeRaw\",\"namespace\":\"com.kubetrade.schema.trade\",\"fields\":[{\"name\":\"tradeKey\",\"type\":{\"type\":\"record\",\"name\":\"TradeKey\",\"fields\":[{\"name\":\"exchange\",\"type\":{\"type\":\"enum\",\"name\":\"Exchange\",\"namespace\":\"com.kubetrade.schema.common\",\"symbols\":[\"GEMINI\"]}},{\"name\":\"key\",\"type\":\"string\"}]}},{\"name\":\"value\",\"type\":{\"type\":\"record\",\"name\":\"TradeValue\",\"fields\":[{\"name\":\"exchange\",\"type\":\"com.kubetrade.schema.common.Exchange\"},{\"name\":\"value\",\"type\":\"string\"}]}},{\"name\":\"symbol\",\"type\":\"string\"},{\"name\":\"payload\",\"type\":\"string\"}]}",
                tradeRawDereferenced);

        // We try to parse the dereferenced schema to ensure it can be used.
        new Schema.Parser().parse(tradeRawDereferenced);

        List<String> tradeRawReferences = clientV3.groups().byGroupId("avro-maven-with-references-auto")
                .artifacts().byArtifactId("TradeRaw").versions().byVersionExpression("2.0").references().get()
                .stream().map(ArtifactReference::getArtifactId).toList();

        Assertions.assertEquals(2, tradeRawReferences.size());
        Assertions.assertTrue(tradeRawReferences.containsAll(
                List.of("com.kubetrade.schema.trade.TradeKey", "com.kubetrade.schema.trade.TradeValue")));

        List<String> tradeKeyReferences = clientV3.groups().byGroupId("avro-maven-with-references-auto")
                .artifacts().byArtifactId("com.kubetrade.schema.trade.TradeKey").versions()
                .byVersionExpression("1").references().get().stream().map(ArtifactReference::getArtifactId)
                .toList();

        Assertions.assertEquals(1, tradeKeyReferences.size());
        Assertions
                .assertTrue(tradeKeyReferences.containsAll(List.of("com.kubetrade.schema.common.Exchange")));

        List<String> tradeValueReferences = clientV3.groups().byGroupId("avro-maven-with-references-auto")
                .artifacts().byArtifactId("com.kubetrade.schema.trade.TradeValue").versions()
                .byVersionExpression("1").references().get().stream().map(ArtifactReference::getArtifactId)
                .toList();

        Assertions.assertEquals(1, tradeValueReferences.size());
        Assertions.assertTrue(
                tradeValueReferences.containsAll(List.of("com.kubetrade.schema.common.Exchange")));
    }

    @Test
    public void testCheckProtobufWithReferences() throws Exception {
        List<String> artifactReferences = clientV3.groups()
                .byGroupId(GroupId.DEFAULT.getRawGroupIdWithDefaultString()).artifacts()
                .byArtifactId("ProtobufSerdeReferencesExample-value").versions().byVersionExpression("1")
                .references().get().stream().map(ArtifactReference::getArtifactId).toList();

        Assertions.assertTrue(artifactReferences.containsAll(List.of("google/protobuf/timestamp.proto",
                "sample/table_info.proto", "sample/table_notification_type.proto")));
        Assertions.assertEquals(3, artifactReferences.size());
    }

    @Test
    public void testCheckJsonWithReferences() throws Exception {
        List<String> artifactReferences = clientV3.groups()
                .byGroupId(GroupId.DEFAULT.getRawGroupIdWithDefaultString()).artifacts()
                .byArtifactId("JsonSerdeReferencesExample").versions().byVersionExpression("1").references()
                .get().stream().map(ArtifactReference::getArtifactId).toList();

        Assertions.assertEquals(4, artifactReferences.size());
        Assertions.assertTrue(artifactReferences
                .containsAll(List.of("city", "qualification", "citizenIdentifier", "address")));

        List<ArtifactReference> cityReferences = clientV3.groups()
                .byGroupId(GroupId.DEFAULT.getRawGroupIdWithDefaultString()).artifacts().byArtifactId("city")
                .versions().byVersionExpression("1").references().get();

        Assertions.assertEquals(1, cityReferences.size());
        Assertions.assertTrue(cityReferences.stream().anyMatch(
                artifactReference -> artifactReference.getArtifactId().equals("cityQualification")));

        List<ArtifactReference> identifierReferences = clientV3.groups()
                .byGroupId(GroupId.DEFAULT.getRawGroupIdWithDefaultString()).artifacts()
                .byArtifactId("citizenIdentifier").versions().byVersionExpression("1").references().get();

        Assertions.assertEquals(1, identifierReferences.size());
        Assertions.assertTrue(identifierReferences.stream().anyMatch(
                artifactReference -> artifactReference.getArtifactId().equals("identifierQualification")));

        /*
         * FIXME:carnalca this cannot be asserted until json schema dereferencing is implemented in v3. The
         * intention here is to make sure that the content can be dereferenced as in Registry v2. String
         * dereferencedContent = IoUtil.toString(clientV3.groups().byGroupId("default")
         * .artifacts().byArtifactId("JsonSerdeReferencesExample") .versions().byVersionExpression("1")
         * .content() .get(configuration -> { configuration.queryParameters.references =
         * HandleReferencesType.DEREFERENCE; }));
         */
    }

    @Test
    public void testLatestBranch() {
        try {
            ArtifactSearchResults results = clientV3.search().artifacts().get();
            results.getArtifacts().forEach(artifact -> {
                String groupId = "default";
                if (artifact.getGroupId() != null) {
                    groupId = artifact.getGroupId();
                }
                BranchMetaData branchMetaData = clientV3.groups().byGroupId(groupId).artifacts()
                        .byArtifactId(artifact.getArtifactId()).branches().byBranchId("latest").get();
                Assertions.assertNotNull(branchMetaData);
                Assertions.assertEquals(artifact.getGroupId(), branchMetaData.getGroupId());
                Assertions.assertEquals(artifact.getArtifactId(), branchMetaData.getArtifactId());
                Assertions.assertEquals("latest", branchMetaData.getBranchId());

                VersionMetaData versionMetaData = clientV3.groups().byGroupId(groupId).artifacts()
                        .byArtifactId(artifact.getArtifactId()).versions()
                        .byVersionExpression("branch=latest").get();
                Assertions.assertNotNull(versionMetaData);
                Assertions.assertEquals(artifact.getGroupId(), versionMetaData.getGroupId());
                Assertions.assertEquals(artifact.getArtifactId(), versionMetaData.getArtifactId());
            });

            // Make sure the latest version of "MixAvroExample/Farewell" is version "2"
            VersionMetaData versionMetaData = clientV3.groups().byGroupId("MixAvroExample").artifacts()
                    .byArtifactId("Farewell").versions().byVersionExpression("branch=latest").get();
            Assertions.assertNotNull(versionMetaData);
            Assertions.assertEquals("2", versionMetaData.getVersion());

            // Make sure the latest version of "default/city" is version "2"
            versionMetaData = clientV3.groups().byGroupId("default").artifacts().byArtifactId("city")
                    .versions().byVersionExpression("branch=latest").get();
            Assertions.assertNotNull(versionMetaData);
            Assertions.assertEquals("2", versionMetaData.getVersion());
        } catch (ProblemDetails e) {
            System.err.println("ERROR: " + e.getDetail());
            throw e;
        }
    }

}
