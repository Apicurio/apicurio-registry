package io.apicurio.registry;

import io.apicurio.registry.model.GroupId;
import io.apicurio.registry.rest.client.models.ArtifactReference;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

@QuarkusTest
@TestProfile(DataUpgradeTestProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class DataUpgradeTest extends AbstractResourceTestBase {

    @Override
    @BeforeEach
    protected void beforeEach() throws Exception {
        setupRestAssured();
    }

    @Test
    public void testArtifactsCount() {
        Assertions.assertEquals(26, clientV3.search().artifacts().get().getCount());
    }

    @Test
    public void testCheckGlobalRules() throws Exception {
        // Global rules are enabled in the export file, they must be activated.
        Assertions.assertEquals(3, clientV3.admin().rules().get().size());
        Assertions.assertEquals("FULL", clientV3.admin().rules().byRuleType("VALIDITY").get().getConfig());
        Assertions.assertEquals("BACKWARD",
                clientV3.admin().rules().byRuleType("COMPATIBILITY").get().getConfig());
        Assertions.assertEquals("FULL", clientV3.admin().rules().byRuleType("INTEGRITY").get().getConfig());
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
