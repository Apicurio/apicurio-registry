package io.apicurio.registry.noprofile;

import static io.apicurio.registry.utils.tests.TestUtils.retry;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import io.apicurio.registry.AbstractResourceTestBase;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.apicurio.registry.rest.client.models.ArtifactContent;
import io.apicurio.registry.rest.client.models.ArtifactMetaData;
import io.apicurio.registry.serde.SerdeConfig;
import io.apicurio.registry.serde.jsonschema.JsonSchemaKafkaDeserializer;
import io.apicurio.registry.serde.jsonschema.JsonSchemaKafkaSerializer;
import io.apicurio.registry.support.Person;
import io.apicurio.registry.types.ArtifactType;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class JsonSerdeTest extends AbstractResourceTestBase {

    @Test
    public void testSchema() throws Exception {
        String groupId = "JsonSerdeTest_testSchema";
        String jsonSchema = new String(getClass().getResourceAsStream("/io/apicurio/registry/util/json-schema.json").readAllBytes(), StandardCharsets.UTF_8);
        Assertions.assertNotNull(jsonSchema);

        String artifactId = generateArtifactId();

        ArtifactContent content = new ArtifactContent();
        content.setContent(jsonSchema);
        ArtifactMetaData amd = clientV3.groups().byGroupId(groupId).artifacts().post(content, config -> {
            config.headers.add("X-Registry-ArtifactId", artifactId + "-value");
            config.headers.add("X-Registry-ArtifactType", ArtifactType.JSON);
        });

        // make sure we have schema registered
        retry(() -> clientV3.ids().globalIds().byGlobalId(amd.getGlobalId()).get());

        Person person = new Person("Ales", "Justin", 23);

        try (JsonSchemaKafkaSerializer<Person> serializer = new JsonSchemaKafkaSerializer<>(clientV3, true);
             JsonSchemaKafkaDeserializer<Person> deserializer = new JsonSchemaKafkaDeserializer<>(clientV3, true)) {

            Map<String, String> configs = Map.of(SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID, groupId);
            serializer.configure(configs, false);

            deserializer.configure(configs, false);

            Headers headers = new RecordHeaders();
            byte[] bytes = serializer.serialize(artifactId, headers, person);

            person = deserializer.deserialize(artifactId, headers, bytes);

            Assertions.assertEquals("Ales", person.getFirstName());
            Assertions.assertEquals("Justin", person.getLastName());
            Assertions.assertEquals(23, person.getAge());

            person.setAge(-1);

            try {
                serializer.serialize(artifactId, new RecordHeaders(), person);
                Assertions.fail();
            } catch (Exception ignored) {
            }

            serializer.setValidationEnabled(false); // disable validation
            // create invalid person bytes
            bytes = serializer.serialize(artifactId, headers, person);

            try {
                deserializer.deserialize(artifactId, headers, bytes);
                Assertions.fail();
            } catch (Exception ignored) {
            }
        }

    }
}
