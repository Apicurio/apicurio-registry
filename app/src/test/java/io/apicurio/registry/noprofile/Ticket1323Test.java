package io.apicurio.registry.noprofile;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateArtifactResponse;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaString;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Reproducer for:  https://issues.redhat.com/browse/IPT-1323
 */
@QuarkusTest
public class Ticket1323Test extends AbstractResourceTestBase {

    private static final String INVALID_AVRO_CONTENT = "{\n" +
            "  \"type\": \"record\",\n" +
            "  \"name\": \"Key\",\n" +
            "  \"namespace\": \"cpr-jdz-private-sltrs_isu-dbo-ISU_EPROFVAL15-rt\",\n" +
            "  \"fields\": [\n" +
            "    {\n" +
            "      \"name\": \"MANDT\",\n" +
            "      \"type\": \"string\"\n" +
            "    },\n" +
            "    {\n" +
            "      \"name\": \"PROFILE\",\n" +
            "      \"type\": \"string\"\n" +
            "    },\n" +
            "    {\n" +
            "      \"name\": \"VALUEDAY\",\n" +
            "      \"type\": \"string\"\n" +
            "    },\n" +
            "    {\n" +
            "      \"name\": \"__dbz__physicalTableIdentifier\",\n" +
            "      \"type\": \"string\"\n" +
            "    }\n" +
            "  ],\n" +
            "  \"connect.name\": \"cpr-jdz-private-sltrs_isu-dbo-ISU_EPROFVAL15-rt.Key\"\n" +
            "}";

    @Test
    public void testInvalidAvroContent() throws Exception {
        String groupId = "default";
        String artifactId = TestUtils.generateArtifactId();

        // Create the invalid schema using the core v3 api
        CreateArtifact createAddress = new CreateArtifact();
        createAddress.setArtifactId(artifactId);
        createAddress.setArtifactType("AVRO");
        createAddress.setFirstVersion(new CreateVersion());
        createAddress.getFirstVersion().setVersion("1");
        createAddress.getFirstVersion().setContent(new VersionContent());
        createAddress.getFirstVersion().getContent().setContent(INVALID_AVRO_CONTENT);
        createAddress.getFirstVersion().getContent().setContentType(ContentTypes.APPLICATION_JSON);
        CreateArtifactResponse car = clientV3.groups().byGroupId(groupId).artifacts().post(createAddress);

        long contentId = car.getVersion().getContentId();

        // Try to get the schema by its contentId via the ccompat API
        SchemaString schemaString = confluentClient.getId((int) contentId);
        Assertions.assertNotNull(schemaString);
        Assertions.assertTrue(schemaString.getSchemaString().contains("ISU_EPROFVAL15"));
    }

}
