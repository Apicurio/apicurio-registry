package io.apicurio.registry.noprofile.proxy;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.resolver.config.SchemaResolverConfig;
import io.apicurio.registry.serde.config.KafkaSerdeConfig;
import io.apicurio.registry.serde.config.SerdeConfig;
import io.apicurio.registry.serde.jsonschema.JsonSchemaKafkaDeserializer;
import io.apicurio.registry.serde.jsonschema.JsonSchemaKafkaSerializer;
import io.apicurio.registry.serde.strategy.SimpleTopicIdStrategy;
import io.apicurio.registry.support.Person;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.Deserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Tests that SerDe classes work with proxy configuration.
 */
@QuarkusTest
public class ProxySerdeTest extends AbstractResourceTestBase {

    private static final int PROXY_PORT = 38081;
    private SimpleTestProxy proxy;

    @Override
    protected void deleteGlobalRules(int expectedDefaultRulesCount) throws Exception {
    }

    @BeforeEach
    public void setupProxy() throws Exception {
        URL url = new URL(registryV3ApiUrl);
        String host = url.getHost();
        int port = url.getPort();
        if (port == -1) {
            // If no port specified, it means we're using the test default port
            // which is typically 8081 for QuarkusTest
            port = 8081;
        }
        proxy = new SimpleTestProxy(PROXY_PORT, host, port);
        proxy.start().get();
        proxy.resetRequestCount();
    }

    @AfterEach
    public void teardownProxy() {
        if (proxy != null) {
            proxy.stop();
        }
    }

    @Test
    public void testJsonSchemaSerdeWithProxy() throws Exception {
        InputStream jsonSchema = getClass()
                .getResourceAsStream("/io/apicurio/registry/util/json-schema.json");
        Assertions.assertNotNull(jsonSchema);

        String groupId = TestUtils.generateGroupId();
        String artifactId = generateArtifactId();

        createArtifact(groupId, artifactId, ArtifactType.JSON, IoUtil.toString(jsonSchema),
                ContentTypes.APPLICATION_JSON);

        Person person = new Person("Bob", "Jones", 30);

        try (JsonSchemaKafkaSerializer<Person> serializer = new JsonSchemaKafkaSerializer<>();
            Deserializer<Person> deserializer = new JsonSchemaKafkaDeserializer<>()) {

            Map<String, Object> commonConfig = new HashMap<>();
            commonConfig.put(SerdeConfig.REGISTRY_URL, registryV3ApiUrl);
            commonConfig.put(SchemaResolverConfig.PROXY_HOST, "localhost");
            commonConfig.put(SchemaResolverConfig.PROXY_PORT, PROXY_PORT);

            Map<String, Object> serializerConfig = new HashMap<>();
            serializerConfig.putAll(commonConfig);
            serializerConfig.put(SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID, groupId);
            serializerConfig.put(SerdeConfig.ARTIFACT_RESOLVER_STRATEGY, SimpleTopicIdStrategy.class.getName());
            serializerConfig.put(KafkaSerdeConfig.ENABLE_HEADERS, "true");
            serializerConfig.put(SerdeConfig.VALIDATION_ENABLED, "true");
            serializer.configure(serializerConfig, false);

            Map<String, Object> deserializerConfig = new HashMap<>();
            deserializerConfig.putAll(commonConfig);
            deserializerConfig.put(KafkaSerdeConfig.ENABLE_HEADERS, "true");
            deserializer.configure(deserializerConfig, false);

            Headers headers = new RecordHeaders();
            byte[] bytes = serializer.serialize(artifactId, headers, person);
            person = deserializer.deserialize(artifactId, headers, bytes);

            Assertions.assertEquals("Bob", person.getFirstName());
            Assertions.assertEquals("Jones", person.getLastName());
            Assertions.assertEquals(30, person.getAge());
            Assertions.assertTrue(proxy.getRequestCount() > 0);
        }
    }
}
