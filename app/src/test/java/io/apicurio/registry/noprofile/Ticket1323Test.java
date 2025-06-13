package io.apicurio.registry.noprofile;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.v2.beans.ArtifactMetaData;
import io.apicurio.registry.utils.tests.TestUtils;
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaString;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static io.apicurio.registry.utils.IoUtil.toStream;

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

        // Create the invalid schema using the core v2 api
        InputStream artifactData = toStream(INVALID_AVRO_CONTENT);
        ArtifactMetaData amd = clientV2.createArtifact(groupId, artifactId, "AVRO", artifactData);

        long contentId = amd.getContentId();

        // Try to get the schema by its contentId via the ccompat API
        SchemaString schemaString = confluentClient.getId((int) contentId);
        Assertions.assertNotNull(schemaString);
        Assertions.assertTrue(schemaString.getSchemaString().contains("ISU_EPROFVAL15"));
    }

}
