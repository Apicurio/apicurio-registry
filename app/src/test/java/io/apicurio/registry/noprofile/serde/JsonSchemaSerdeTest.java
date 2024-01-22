package io.apicurio.registry.noprofile.serde;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;


import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.resolver.DefaultSchemaResolver;
import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.SchemaResolver;
import io.apicurio.registry.resolver.SchemaResolverConfig;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.ArtifactContent;
import io.apicurio.registry.rest.client.models.ArtifactMetaData;
import io.apicurio.registry.rest.client.models.ArtifactReference;
import io.apicurio.registry.rest.client.models.IfExists;
import io.apicurio.registry.serde.SchemaResolverConfigurer;
import io.apicurio.registry.serde.SerdeConfig;
import io.apicurio.registry.serde.SerdeHeaders;
import io.apicurio.registry.serde.jsonschema.JsonSchema;
import io.apicurio.registry.serde.jsonschema.JsonSchemaKafkaDeserializer;
import io.apicurio.registry.serde.jsonschema.JsonSchemaKafkaSerializer;
import io.apicurio.registry.serde.jsonschema.JsonSchemaParser;
import io.apicurio.registry.serde.strategy.SimpleTopicIdStrategy;
import io.apicurio.registry.support.Citizen;
import io.apicurio.registry.support.CitizenIdentifier;
import io.apicurio.registry.support.City;
import io.apicurio.registry.support.Person;
import io.apicurio.registry.support.Qualification;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.tests.TestUtils;
import io.kiota.http.vertx.VertXRequestAdapter;
import io.quarkus.test.junit.QuarkusTest;
import io.apicurio.registry.client.auth.VertXAuthFactory;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.Deserializer;
import org.everit.json.schema.ValidationException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
public class JsonSchemaSerdeTest extends AbstractResourceTestBase {

    private RegistryClient restClient;

    @BeforeEach
    public void createIsolatedClient() {
        var adapter = new VertXRequestAdapter(VertXAuthFactory.defaultVertx);
        adapter.setBaseUrl(TestUtils.getRegistryV3ApiUrl(testPort));
        restClient = new RegistryClient(adapter);
    }

    @Test
    public void testJsonSchemaSerde() throws Exception {
        InputStream jsonSchema = getClass().getResourceAsStream("/io/apicurio/registry/util/json-schema.json");
        Assertions.assertNotNull(jsonSchema);

        String groupId = TestUtils.generateGroupId();
        String artifactId = generateArtifactId();

        createArtifact(groupId, artifactId, ArtifactType.JSON, IoUtil.toString(jsonSchema));

        Person person = new Person("Ales", "Justin", 23);

        try (JsonSchemaKafkaSerializer<Person> serializer = new JsonSchemaKafkaSerializer<>(restClient, true);
             Deserializer<Person> deserializer = new JsonSchemaKafkaDeserializer<>(restClient, true)) {

            Map<String, Object> config = new HashMap<>();
            config.put(SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID, groupId);
            config.put(SerdeConfig.ARTIFACT_RESOLVER_STRATEGY, SimpleTopicIdStrategy.class.getName());
            serializer.configure(config, false);

            deserializer.configure(Collections.emptyMap(), false);

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

    @Test
    public void testJsonSchemaSerdeAutoRegister() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = generateArtifactId();

        Person person = new Person("Carles", "Arnal", 30);

        try (JsonSchemaKafkaSerializer<Person> serializer = new JsonSchemaKafkaSerializer<>(restClient, true);
             Deserializer<Person> deserializer = new JsonSchemaKafkaDeserializer<>(restClient, true)) {

            Map<String, Object> config = new HashMap<>();
            config.put(SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID, groupId);
            config.put(SerdeConfig.ARTIFACT_RESOLVER_STRATEGY, SimpleTopicIdStrategy.class.getName());
            config.put(SerdeConfig.SCHEMA_LOCATION, "/io/apicurio/registry/util/json-schema.json");
            config.put(SerdeConfig.AUTO_REGISTER_ARTIFACT, true);
            serializer.configure(config, false);

            deserializer.configure(Collections.emptyMap(), false);

            Headers headers = new RecordHeaders();
            byte[] bytes = serializer.serialize(artifactId, headers, person);

            person = deserializer.deserialize(artifactId, headers, bytes);

            Assertions.assertEquals("Carles", person.getFirstName());
            Assertions.assertEquals("Arnal", person.getLastName());
            Assertions.assertEquals(30, person.getAge());

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

    @Test
    public void testJsonSchemaSerdeHeaders() throws Exception {
        InputStream jsonSchema = getClass().getResourceAsStream("/io/apicurio/registry/util/json-schema.json");
        Assertions.assertNotNull(jsonSchema);

        String groupId = TestUtils.generateGroupId();
        String artifactId = generateArtifactId();

        Long globalId = createArtifact(groupId, artifactId, ArtifactType.JSON, IoUtil.toString(jsonSchema));

        Person person = new Person("Ales", "Justin", 23);

        try (JsonSchemaKafkaSerializer<Person> serializer = new JsonSchemaKafkaSerializer<>(restClient, true);
             Deserializer<Person> deserializer = new JsonSchemaKafkaDeserializer<>(restClient, true)) {

            Map<String, Object> config = new HashMap<>();
            config.put(SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID, groupId);
            config.put(SerdeConfig.ARTIFACT_RESOLVER_STRATEGY, SimpleTopicIdStrategy.class.getName());
            serializer.configure(config, false);

            deserializer.configure(Collections.emptyMap(), false);

            Headers headers = new RecordHeaders();
            byte[] bytes = serializer.serialize(artifactId, headers, person);

            Assertions.assertNotNull(headers.lastHeader(SerdeHeaders.HEADER_VALUE_GLOBAL_ID));
            Header headerGlobalId = headers.lastHeader(SerdeHeaders.HEADER_VALUE_GLOBAL_ID);
            long id = ByteBuffer.wrap(headerGlobalId.value()).getLong();
            assertEquals(globalId.intValue(), Long.valueOf(id).intValue());

            Assertions.assertNotNull(headers.lastHeader(SerdeHeaders.HEADER_VALUE_MESSAGE_TYPE));
            Header headerMsgType = headers.lastHeader(SerdeHeaders.HEADER_VALUE_MESSAGE_TYPE);
            assertEquals(person.getClass().getName(), IoUtil.toString(headerMsgType.value()));

            person = deserializer.deserialize(artifactId, headers, bytes);

            Assertions.assertEquals("Ales", person.getFirstName());
            Assertions.assertEquals("Justin", person.getLastName());
            Assertions.assertEquals(23, person.getAge());
        }

    }

    @Test
    public void testJsonSchemaSerdeMagicByte() throws Exception {

        InputStream jsonSchema = getClass().getResourceAsStream("/io/apicurio/registry/util/json-schema-with-java-type.json");
        Assertions.assertNotNull(jsonSchema);

        String groupId = TestUtils.generateGroupId();
        String artifactId = generateArtifactId();

        Long globalId = createArtifact(groupId, artifactId, ArtifactType.JSON, IoUtil.toString(jsonSchema));

        Person person = new Person("Ales", "Justin", 23);

        try (JsonSchemaKafkaSerializer<Person> serializer = new JsonSchemaKafkaSerializer<>(restClient, true);
             Deserializer<Person> deserializer = new JsonSchemaKafkaDeserializer<>(restClient, true)) {

            Map<String, Object> config = new HashMap<>();
            config.put(SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID, groupId);
            config.put(SerdeConfig.ARTIFACT_RESOLVER_STRATEGY, SimpleTopicIdStrategy.class.getName());
            serializer.configure(config, false);

            deserializer.configure(Collections.emptyMap(), false);

            byte[] bytes = serializer.serialize(artifactId, person);

            TestUtils.waitForSchema(schemaGlobalId -> {
                assertEquals(globalId.intValue(), schemaGlobalId.intValue());
                return true;
            }, bytes);

            person = deserializer.deserialize(artifactId, bytes);

            Assertions.assertEquals("Ales", person.getFirstName());
            Assertions.assertEquals("Justin", person.getLastName());
            Assertions.assertEquals(23, person.getAge());
        }
    }

    @Test
    public void testJsonSchemaSerdeWithReferences() throws Exception {
        InputStream citySchema = getClass().getResourceAsStream("/io/apicurio/registry/util/city.json");
        InputStream citizenSchema = getClass().getResourceAsStream("/io/apicurio/registry/util/citizen.json");
        InputStream citizenIdentifier = getClass().getResourceAsStream("/io/apicurio/registry/util/citizenIdentifier.json");
        InputStream qualificationSchema = getClass().getResourceAsStream("/io/apicurio/registry/util/qualification.json");

        InputStream addressSchema = getClass().getResourceAsStream("/io/apicurio/registry/util/sample.address.json");

        Assertions.assertNotNull(citizenSchema);
        Assertions.assertNotNull(citySchema);
        Assertions.assertNotNull(citizenIdentifier);
        Assertions.assertNotNull(qualificationSchema);
        Assertions.assertNotNull(addressSchema);


        String groupId = TestUtils.generateGroupId();
        String cityArtifactId = generateArtifactId();
        String qualificationsId = generateArtifactId();
        String identifierArtifactId = generateArtifactId();
        String addressId = generateArtifactId();


        createArtifact(groupId, cityArtifactId, ArtifactType.JSON, IoUtil.toString(citySchema));

        createArtifact(groupId, qualificationsId, ArtifactType.JSON, IoUtil.toString(qualificationSchema));

        final  io.apicurio.registry.rest.v3.beans.ArtifactReference qualificationsReference = new  io.apicurio.registry.rest.v3.beans.ArtifactReference();
        qualificationsReference.setVersion("1");
        qualificationsReference.setGroupId(groupId);
        qualificationsReference.setArtifactId(qualificationsId);
        qualificationsReference.setName("qualification.json");

        createArtifact(groupId, addressId, ArtifactType.JSON, IoUtil.toString(addressSchema));

        final  io.apicurio.registry.rest.v3.beans.ArtifactReference addressReference = new  io.apicurio.registry.rest.v3.beans.ArtifactReference();
        addressReference.setVersion("1");
        addressReference.setGroupId(groupId);
        addressReference.setArtifactId(addressId);
        addressReference.setName("sample.address.json");

        final  io.apicurio.registry.rest.v3.beans.ArtifactReference cityReference = new  io.apicurio.registry.rest.v3.beans.ArtifactReference();
        cityReference.setVersion("1");
        cityReference.setGroupId(groupId);
        cityReference.setArtifactId(cityArtifactId);
        cityReference.setName("city.json");

        createArtifact(groupId, identifierArtifactId, ArtifactType.JSON, IoUtil.toString(citizenIdentifier));

        final  io.apicurio.registry.rest.v3.beans.ArtifactReference identifierReference = new  io.apicurio.registry.rest.v3.beans.ArtifactReference();
        identifierReference.setVersion("1");
        identifierReference.setGroupId(groupId);
        identifierReference.setArtifactId(identifierArtifactId);
        identifierReference.setName("citizenIdentifier.json");

        String artifactId = generateArtifactId();

        createArtifactWithReferences(groupId, artifactId, ArtifactType.JSON, IoUtil.toString(citizenSchema), List.of(qualificationsReference, cityReference, identifierReference, addressReference));

        City city = new City("New York", 10001);
        CitizenIdentifier identifier = new CitizenIdentifier(123456789);
        Citizen citizen = new Citizen("Carles", "Arnal", 23, city, identifier, Collections.emptyList());

        try (JsonSchemaKafkaSerializer<Citizen> serializer = new JsonSchemaKafkaSerializer<>(restClient, true);
             Deserializer<Citizen> deserializer = new JsonSchemaKafkaDeserializer<>(restClient, true)) {

            Map<String, Object> config = new HashMap<>();
            config.put(SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID, groupId);
            config.put(SerdeConfig.ARTIFACT_RESOLVER_STRATEGY, SimpleTopicIdStrategy.class.getName());
            serializer.configure(config, false);

            deserializer.configure(Collections.emptyMap(), false);

            Headers headers = new RecordHeaders();
            byte[] bytes = serializer.serialize(artifactId, headers, citizen);

            citizen = deserializer.deserialize(artifactId, headers, bytes);

            Assertions.assertEquals("Carles", citizen.getFirstName());
            Assertions.assertEquals("Arnal", citizen.getLastName());
            Assertions.assertEquals(23, citizen.getAge());
            Assertions.assertEquals("New York", citizen.getCity().getName());

            citizen.setAge(-1);

            try {
                serializer.serialize(artifactId, new RecordHeaders(), citizen);
                Assertions.fail();
            } catch (Exception ignored) {
            }

            citizen.setAge(23);
            city = new City("Kansas CIty", -31);
            citizen.setCity(city);

            try {
                serializer.serialize(artifactId, new RecordHeaders(), citizen);
                Assertions.fail();
            } catch (Exception ignored) {
            }


            //invalid identifier present, should fail
            identifier = new CitizenIdentifier(-1234356);
            citizen.setIdentifier(identifier);

            city = new City("Kansas CIty", 22222);
            citizen.setCity(city);

            try {
                serializer.serialize(artifactId, new RecordHeaders(), citizen);
                Assertions.fail();
            } catch (Exception ignored) {
            }

            //no identifier present, should pass
            citizen.setIdentifier(null);
            serializer.serialize(artifactId, new RecordHeaders(), citizen);

            //valid qualification, should pass
            citizen.setQualifications(List.of(new Qualification(UUID.randomUUID().toString(), 6), new Qualification(UUID.randomUUID().toString(), 7), new Qualification(UUID.randomUUID().toString(), 8)));
            serializer.serialize(artifactId, new RecordHeaders(), citizen);

            //invalid qualification, should fail
            citizen.setQualifications(List.of(new Qualification(UUID.randomUUID().toString(), 6), new Qualification(UUID.randomUUID().toString(), -7), new Qualification(UUID.randomUUID().toString(), 8)));
            try {
                serializer.serialize(artifactId, new RecordHeaders(), citizen);
                Assertions.fail();
            } catch (Exception ignored) {
            }
        }
    }

    @Test
    public void complexObjectValidation() throws Exception {
        String version = "8";

        RegistryClient client = createRestClientV3();

        InputStream account = getClass().getClassLoader()
                .getResourceAsStream("/io/apicurio/registry/util/sample.account.json");
        InputStream address = getClass().getClassLoader()
                .getResourceAsStream("/io/apicurio/registry/util/sample.address.json");
        InputStream email = getClass().getClassLoader()
                .getResourceAsStream("/io/apicurio/registry/util/sample.email.json");
        InputStream phone = getClass().getClassLoader()
                .getResourceAsStream("/io/apicurio/registry/util/sample.phone.json");

        Assertions.assertNotNull(account);
        Assertions.assertNotNull(address);
        Assertions.assertNotNull(email);
        Assertions.assertNotNull(phone);

        final ArtifactContent content = new ArtifactContent();
        content.setContent(new String(address.readAllBytes(), StandardCharsets.UTF_8));
        final ArtifactMetaData amdAddress =
                client
                        .groups()
                        .byGroupId("GLOBAL")
                        .artifacts()
                        .post(content, config -> {
                            config.queryParameters.ifExists = IfExists.UPDATE;
                            config.queryParameters.canonical = false;
                            config.headers.add("X-Registry-ArtifactId", "sample.address.json");
                            config.headers.add("X-Registry-ArtifactType", ArtifactType.JSON);
                            config.headers.add("X-Registry-Version", version);
                            config.headers.add("Content-Type", "application/create.extended+json");
                        });

        content.setContent(new String(email.readAllBytes(), StandardCharsets.UTF_8));
        final ArtifactMetaData amdEmail =
                client
                        .groups()
                        .byGroupId("GLOBAL")
                        .artifacts()
                        .post(content, config -> {
                            config.queryParameters.ifExists = IfExists.UPDATE;
                            config.queryParameters.canonical = false;
                            config.headers.add("X-Registry-ArtifactId", "sample.email.json");
                            config.headers.add("X-Registry-ArtifactType", ArtifactType.JSON);
                            config.headers.add("X-Registry-Version", version);
                            config.headers.add("Content-Type", "application/create.extended+json");
                        });

        content.setContent(new String(phone.readAllBytes(), StandardCharsets.UTF_8));
        final ArtifactMetaData amdPhone =
                client
                        .groups()
                        .byGroupId("GLOBAL")
                        .artifacts()
                        .post(content, config -> {
                            config.queryParameters.ifExists = IfExists.UPDATE;
                            config.queryParameters.canonical = false;
                            config.headers.add("X-Registry-ArtifactId", "sample.phone.json");
                            config.headers.add("X-Registry-ArtifactType", ArtifactType.JSON);
                            config.headers.add("X-Registry-Version", version);
                            config.headers.add("Content-Type", "application/create.extended+json");
                        });


        final ArtifactReference addressReference = new ArtifactReference();
        addressReference.setVersion(amdAddress.getVersion());
        addressReference.setGroupId(amdAddress.getGroupId());
        addressReference.setArtifactId(amdAddress.getId());
        addressReference.setName("sample.address.json");

        final ArtifactReference emailReference = new ArtifactReference();
        emailReference.setVersion(amdEmail.getVersion());
        emailReference.setGroupId(amdEmail.getGroupId());
        emailReference.setArtifactId(amdEmail.getId());
        emailReference.setName("sample.email.json");

        final ArtifactReference phoneReference = new ArtifactReference();
        phoneReference.setVersion(amdPhone.getVersion());
        phoneReference.setGroupId(amdPhone.getGroupId());
        phoneReference.setArtifactId(amdPhone.getId());
        phoneReference.setName("sample.phone.json");

        List<ArtifactReference> artifactReferences = new ArrayList<>();

        artifactReferences.add(addressReference);
        artifactReferences.add(emailReference);
        artifactReferences.add(phoneReference);

        content.setContent(new String(account.readAllBytes(), StandardCharsets.UTF_8));
        content.setReferences(artifactReferences);
        client
                .groups()
                .byGroupId("GLOBAL")
                .artifacts()
                .post(content, config -> {
                    config.queryParameters.ifExists = IfExists.UPDATE;
                    config.queryParameters.canonical = false;
                    config.headers.add("X-Registry-ArtifactId", "sample.account.json");
                    config.headers.add("X-Registry-ArtifactType", ArtifactType.JSON);
                    config.headers.add("X-Registry-Version", version);
                    config.headers.add("Content-Type", "application/create.extended+json");
                });

        String data = "{\n" + "  \"id\": \"abc\",\n" + "  \n" + "  \"accountPhones\": [{\n"
                + "  \"phoneRelationTypeCd\": \"ABCDEFGHIJ\",\n"
                + "  \"effectiveDate\": \"201-09-29T18:46:19Z\"\n" + "  \n" + "  \n" + "  }]\n" + "}";

        ObjectMapper objectMapper = new ObjectMapper().configure(
                DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        JsonNode validationFor = objectMapper.readTree(data);

        ArtifactMetaData global =
            client.groups().byGroupId("GLOBAL").artifacts().byArtifactId("sample.account.json").meta().get();
                // client.getArtifactMetaData("GLOBAL", "sample.account.json");
        io.apicurio.registry.resolver.strategy.ArtifactReference artifactReference = io.apicurio.registry.resolver.strategy.ArtifactReference.builder().globalId(global.getGlobalId())
                .groupId("GLOBAL")//.version("4")
                .artifactId("sample.account.json").build();

        SchemaResolverConfigurer<JsonSchema, Object> src = new SchemaResolverConfigurer<JsonSchema, Object>(client);

        SchemaResolver<JsonSchema, Object> sr = src.getSchemaResolver();
        Map<String, String> configs = new HashMap<>();
        configs.put(SchemaResolverConfig.ARTIFACT_RESOLVER_STRATEGY_DEFAULT,
                DefaultSchemaResolver.class.getName());
        configs.put(SchemaResolverConfig.CHECK_PERIOD_MS, "600000");
        sr.configure(configs, new JsonSchemaParser<Object>());
        ParsedSchema<JsonSchema> ps = sr.resolveSchemaByArtifactReference((artifactReference)).getParsedSchema();

        validateDataWithSchema(ps, objectMapper.writeValueAsBytes(validationFor), objectMapper);
    }

    protected static void validateDataWithSchema(ParsedSchema<JsonSchema> schema, byte[] data,
                                                 ObjectMapper mapper) throws IOException {
        try {
            schema.getParsedSchema().validate(mapper.readTree(data));
        } catch (ValidationException e) {
            System.out.println(e.getAllMessages());
        }
    }
}
