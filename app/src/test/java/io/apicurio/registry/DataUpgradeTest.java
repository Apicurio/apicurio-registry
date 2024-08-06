package io.apicurio.registry;

import io.apicurio.registry.model.GroupId;
import io.apicurio.registry.rest.client.models.ArtifactReference;
import io.apicurio.registry.utils.IoUtil;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.anything;

@QuarkusTest
public class DataUpgradeTest extends AbstractResourceTestBase {

    @Override
    @BeforeAll
    protected void beforeAll() throws Exception {
        super.beforeAll();

        setupRestAssured();

        try (InputStream data = resourceToInputStream("./upgrade/v2_export.zip")) {
            given().when().contentType("application/zip").body(data).post("/registry/v2/admin/import").then()
                    .statusCode(204).body(anything());
        }
    }

    @Test
    public void testArtifactsCount() {
        Assertions.assertEquals(32, clientV3.search().artifacts().get().getCount());
    }

    @Test
    public void testCheckAvroWithReferences() throws Exception {
        String dereferencedContent = IoUtil.toString(clientV3.groups().byGroupId("default").artifacts()
                .byArtifactId("AvroSerdeReferencesExample-value").versions().byVersionExpression("1")
                .content().get());

        Assertions.assertEquals(
                "{\"type\":\"record\",\"name\":\"TradeRaw\",\"namespace\":\"com.kubetrade.schema.trade\",\"fields\":[{\"name\":\"tradeKey\",\"type\":{\"type\":\"record\",\"name\":\"TradeKey\",\"fields\":[{\"name\":\"exchange\",\"type\":{\"type\":\"enum\",\"name\":\"Exchange\",\"namespace\":\"com.kubetrade.schema.common\",\"symbols\":[\"GEMINI\"]}},{\"name\":\"key\",\"type\":{\"type\":\"string\",\"avro.java.string\":\"String\"}}]}},{\"name\":\"symbol\",\"type\":{\"type\":\"string\",\"avro.java.string\":\"String\"}},{\"name\":\"payload\",\"type\":{\"type\":\"string\",\"avro.java.string\":\"String\"}}]}",
                dereferencedContent);
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
}
