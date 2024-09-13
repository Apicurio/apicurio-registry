package io.apicurio.registry.noprofile;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.resolver.SchemaResolverConfig;
import io.apicurio.registry.serde.config.KafkaSerdeConfig;
import io.apicurio.registry.serde.config.SerdeConfig;
import io.apicurio.registry.serde.jsonschema.JsonSchemaKafkaDeserializer;
import io.apicurio.registry.serde.jsonschema.JsonSchemaKafkaSerializer;
import io.apicurio.registry.support.Person;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static io.apicurio.registry.utils.tests.TestUtils.retry;

@QuarkusTest
public class JsonSerdeTest extends AbstractResourceTestBase {

    @Test
    public void testSchema() throws Exception {
        String groupId = "JsonSerdeTest_testSchema";
        String jsonSchema = new String(
                getClass().getResourceAsStream("/io/apicurio/registry/util/json-schema.json").readAllBytes(),
                StandardCharsets.UTF_8);
        Assertions.assertNotNull(jsonSchema);

        String artifactId = generateArtifactId();

        long globalId = createArtifact(groupId, artifactId + "-value", ArtifactType.JSON, jsonSchema,
                ContentTypes.APPLICATION_JSON).getVersion().getGlobalId();

        // make sure we have schema registered
        retry(() -> clientV3.ids().globalIds().byGlobalId(globalId).get());

        Person person = new Person("Ales", "Justin", 23);

        try (JsonSchemaKafkaSerializer<Person> serializer = new JsonSchemaKafkaSerializer<>(clientV3);
            JsonSchemaKafkaDeserializer<Person> deserializer = new JsonSchemaKafkaDeserializer<>(clientV3)) {

            Map<String, String> configs = Map.of(SchemaResolverConfig.EXPLICIT_ARTIFACT_GROUP_ID, groupId,
                    KafkaSerdeConfig.ENABLE_HEADERS, "true", SerdeConfig.VALIDATION_ENABLED, "true");
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
