package io.apicurio.registry.noprofile.ccompat.rest.v7;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.VersionSearchResults;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.confluent.kafka.schemaregistry.SchemaProvider;
import io.confluent.kafka.schemaregistry.avro.AvroSchemaProvider;
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaMetadata;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.rest.RestService;
import io.confluent.kafka.schemaregistry.client.rest.entities.Schema;
import io.confluent.kafka.schemaregistry.json.JsonSchemaProvider;
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchemaProvider;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Test to verify that version numbering is consistent between the Core Registry v3 API
 * and the Confluent Compatible v7 API.
 *
 * @see <a href="https://github.com/Apicurio/apicurio-registry/issues/6741">Issue #6741</a>
 */
@QuarkusTest
public class VersionNumberingTest extends AbstractResourceTestBase {

    /**
     * Builds a Confluent Schema Registry client for testing.
     */
    private SchemaRegistryClient confluentClient() {
        final List<SchemaProvider> schemaProviders = Arrays.asList(
            new JsonSchemaProvider(),
            new AvroSchemaProvider(),
            new ProtobufSchemaProvider()
        );
        return new CachedSchemaRegistryClient(
            new RestService("http://localhost:" + testPort + "/apis/ccompat/v7"),
            3,
            schemaProviders,
            null,
            Map.of()
        );
    }

    @Test
    public void testFirstVersionNumbering() throws Exception {
        final String schemaString = "{\"type\":\"record\",\"name\":\"TestRecord\",\"fields\":[{\"name\":\"field1\",\"type\":\"string\"}]}";
        final SchemaRegistryClient confluentClient = confluentClient();
        final String groupId = "default";
        final String artifactId = TestUtils.generateArtifactId();
        final String type = ArtifactType.AVRO;
        final String subject = artifactId;

        // Create an artifact using the Core API
        createArtifact(groupId, artifactId, type, schemaString, ContentTypes.APPLICATION_JSON);

        // List versions of the artifact
        VersionSearchResults versions = clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId(artifactId).versions().get();
        Assertions.assertEquals(1, versions.getCount());
        Assertions.assertEquals(1, versions.getVersions().size());
        Assertions.assertEquals("1", versions.getVersions().get(0).getVersion());

        // List versions using ccompat
        List<Integer> ccompatVersions = confluentClient.getAllVersions(subject);
        Assertions.assertEquals(1, ccompatVersions.size());
        Assertions.assertEquals(1, ccompatVersions.get(0).intValue());

        // Get the latest version using ccompat
        SchemaMetadata latestSchemaMetadata = confluentClient.getLatestSchemaMetadata(subject);
        Assertions.assertNotNull(latestSchemaMetadata);
        Assertions.assertEquals(1, latestSchemaMetadata.getVersion());

        // Get version 1 using ccompat
        Schema byVersion = confluentClient.getByVersion(subject, 1, false);
        Assertions.assertNotNull(byVersion);
        Assertions.assertEquals(1, byVersion.getVersion());

        // Get version 0 using ccompat (404)
        Throwable exception = Assertions.assertThrows(Throwable.class, () -> {
            confluentClient.getByVersion(subject, 0, false);
        });
        Throwable rootCause = TestUtils.getRootCause(exception);
        Assertions.assertTrue(rootCause.getMessage().contains("No version '0' found"));
    }


}