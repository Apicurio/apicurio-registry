package io.apicurio.registry.noprofile.ccompat.rest.v7;

import io.apicurio.registry.AbstractResourceTestBase;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class ConfluentClientJsonTest extends AbstractResourceTestBase {

    /*

    private static final Random random = new Random();

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void testBasic() throws Exception {
        String subject1 = "testTopic1";
        String subject2 = "testTopic2";
        int schemasInSubject1 = 10;
        List<Integer> allVersionsInSubject1 = new ArrayList<>();
        List<String> allSchemasInSubject1 = getRandomJsonSchemas(schemasInSubject1);
        int schemasInSubject2 = 5;
        List<Integer> allVersionsInSubject2 = new ArrayList<>();
        List<String> allSchemasInSubject2 = getRandomJsonSchemas(schemasInSubject2);
        List<String> allSubjects = new ArrayList<String>();

        // test registering and verifying new schemas in subject1
        int schemaIdCounter = 1;
        for (int i = 0; i < schemasInSubject1; i++) {
            String schema = allSchemasInSubject1.get(i);
            int expectedVersion = i + 1;
            registerAndVerifySchema(confluentClient, schema, schemaIdCounter, subject1);
            schemaIdCounter++;
            allVersionsInSubject1.add(expectedVersion);
        }
        allSubjects.add(subject1);

        // test re-registering existing schemas
        for (int i = 0; i < schemasInSubject1; i++) {
            int expectedId = i + 1;
            String schemaString = allSchemasInSubject1.get(i);
            int foundId = confluentClient.registerSchema(schemaString,
                    JsonSchema.TYPE,
                    Collections.emptyList(),
                    subject1
            );
            assertEquals(expectedId, foundId, "Re-registering an existing schema should return the existing version");
        }

        // test registering schemas in subject2
        for (int i = 0; i < schemasInSubject2; i++) {
            String schema = allSchemasInSubject2.get(i);
            int expectedVersion = i + 1;
            registerAndVerifySchema(confluentClient, schema, schemaIdCounter, subject2);
            schemaIdCounter++;
            allVersionsInSubject2.add(expectedVersion);
        }
        allSubjects.add(subject2);

        // test getAllVersions with existing data
        assertEquals(allVersionsInSubject1, confluentClient.getAllVersions(subject1));
        assertEquals(allVersionsInSubject2, confluentClient.getAllVersions(subject2));

        // test getAllSubjects with existing data
        assertEquals(allSubjects, confluentClient.getAllSubjects());
    }

    @Test
    public void testSchemaReferences() throws Exception {
        Map<String, String> schemas = getJsonSchemaWithReferences();
        String subject = "reference";

        RegisterSchemaRequest reference = new RegisterSchemaRequest();
        reference.setSchema(schemas.get("ref.json"));
        reference.setSchemaType(JsonSchema.TYPE);
        int referenceId = confluentClient.registerSchema(reference, subject, false);

        RegisterSchemaRequest request = new RegisterSchemaRequest();
        request.setSchema(schemas.get("main.json"));
        request.setSchemaType(JsonSchema.TYPE);
        SchemaReference ref = new SchemaReference("ref.json", "reference", 1);
        request.setReferences(Collections.singletonList(ref));
        int registeredId = confluentClient.registerSchema(request, "referrer", false);
        assertEquals(2, registeredId, "Registering a new schema should succeed");

        SchemaString schemaString = confluentClient.getId(2);
        assertEquals(MAPPER.readTree(schemas.get("main.json")), MAPPER.readTree(schemaString.getSchemaString()), "Registered schema should be found");

        assertEquals(Collections.singletonList(ref), schemaString.getReferences(), "Schema references should be found");

        List<Integer> refs = confluentClient.getReferencedBy("reference", 1);
        assertEquals(2, refs.get(0).intValue());

        CachedSchemaRegistryClient schemaRegistryClient = new CachedSchemaRegistryClient(
                confluentClient, 10,
                Collections.singletonList(new JsonSchemaProvider()), new HashMap<>(), null);
        SchemaHolder holder = new SchemaHolder();
        JsonSchema schema = JsonSchemaUtils.getSchema(holder, schemaRegistryClient);
        Schema registeredSchema = confluentClient.lookUpSubjectVersion(schema.canonicalString(),
                JsonSchema.TYPE, schema.references(), "referrer", false);
        assertEquals(2, registeredSchema.getId().intValue(), "Registered schema should be found");

        try {
            confluentClient.deleteSchemaVersion(RestService.DEFAULT_REQUEST_PROPERTIES,
                    "reference",
                    String.valueOf(1)
            );
            Assertions.fail("Deleting reference should fail with " + ErrorCode.REFERENCE_EXISTS.value());
        } catch (RestClientException rce) {
            assertEquals(ErrorCode.REFERENCE_EXISTS.value(), rce.getErrorCode());
        }

        assertEquals((Integer) 1, confluentClient
                .deleteSchemaVersion
                        (RestService.DEFAULT_REQUEST_PROPERTIES, "referrer", "1"));

        refs = confluentClient.getReferencedBy("reference", 1);
        Assertions.assertTrue(refs.isEmpty());

        assertEquals((Integer) 1, confluentClient
                .deleteSchemaVersion
                        (RestService.DEFAULT_REQUEST_PROPERTIES, "reference", "1"));
    }

    @io.confluent.kafka.schemaregistry.annotations.Schema(value = "{"
            + "\"$id\": \"https://acme.com/referrer.json\","
            + "\"$schema\": \"http://json-schema.org/draft-07/schema#\","
            + "\"type\":\"object\","
            + "\"properties\":{\"Ref\":"
            + "{\"$ref\":\"ref.json#/definitions/ExternalType\"}},\"additionalProperties\":false}",
            refs = {@io.confluent.kafka.schemaregistry.annotations.SchemaReference(
                    name = "ref.json", subject = "reference")})
    static class SchemaHolder {
        // This is a dummy schema holder to be used for its annotations
    }

    @Test
    public void testSchemaMissingReferences() throws Exception {
        Map<String, String> schemas = getJsonSchemaWithReferences();

        RegisterSchemaRequest request = new RegisterSchemaRequest();
        request.setSchema(schemas.get("main.json"));
        request.setSchemaType(JsonSchema.TYPE);
        request.setReferences(Collections.emptyList());
        Assertions.assertThrows(RestClientException.class, () -> {
            confluentClient.registerSchema(request, "referrer", false);
        });
    }

    @Test
    public void testSchemaNormalization() throws Exception {
        String subject1 = "testSubject1";

        String reference1 = "{\"type\":\"object\",\"additionalProperties\":false,\"definitions\":"
                + "{\"ExternalType\":{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"}},"
                + "\"additionalProperties\":false}}}";
        registerAndVerifySchema(confluentClient, reference1, 1, "ref1");
        String reference2 = "{\"type\":\"object\",\"additionalProperties\":false,\"definitions\":"
                + "{\"ExternalType2\":{\"type\":\"object\",\"properties\":{\"name2\":{\"type\":\"string\"}},"
                + "\"additionalProperties\":false}}}";
        registerAndVerifySchema(confluentClient, reference2, 2, "ref2");

        SchemaReference ref1 = new SchemaReference("ref1.json", "ref1", 1);
        SchemaReference ref2 = new SchemaReference("ref2.json", "ref2", 1);

        // Two versions of same schema
        String schemaString1 = "{"
                + "\"$id\": \"https://acme.com/referrer.json\","
                + "\"$schema\": \"http://json-schema.org/draft-07/schema#\","
                + "\"type\":\"object\",\"properties\":{"
                + "\"Ref1\": {\"$ref\":\"ref1.json#/definitions/ExternalType\"},"
                + "\"Ref2\": {\"$ref\":\"ref2.json#/definitions/ExternalType2\"}"
                + "},\"additionalProperties\":false}";
        String schemaString2 = "{"
                + "\"$schema\": \"http://json-schema.org/draft-07/schema#\","
                + "\"$id\": \"https://acme.com/referrer.json\","
                + "\"type\":\"object\",\"properties\":{"
                + "\"Ref2\": {\"$ref\":\"ref2.json#/definitions/ExternalType2\"},"
                + "\"Ref1\": {\"$ref\":\"ref1.json#/definitions/ExternalType\"}"
                + "},\"additionalProperties\":false}";

        RegisterSchemaRequest registerRequest = new RegisterSchemaRequest();
        registerRequest.setSchema(schemaString1);
        registerRequest.setSchemaType(JsonSchema.TYPE);
        registerRequest.setReferences(Arrays.asList(ref1, ref2));
        int idOfRegisteredSchema1Subject1 =
                confluentClient.registerSchema(registerRequest, subject1, true);
        RegisterSchemaRequest lookUpRequest = new RegisterSchemaRequest();
        lookUpRequest.setSchema(schemaString2);
        lookUpRequest.setSchemaType(JsonSchema.TYPE);
        lookUpRequest.setReferences(Arrays.asList(ref2, ref1));
        int versionOfRegisteredSchema1Subject1 =
                confluentClient.lookUpSubjectVersion(lookUpRequest, subject1, true, false).getVersion();
        assertEquals(1, versionOfRegisteredSchema1Subject1, "1st schema under subject1 should have version 1");
        assertEquals(3, idOfRegisteredSchema1Subject1, "1st schema registered globally should have id 3");
    }

    @Test
    public void testBad() throws Exception {
        String subject1 = "testTopic1";
        List<String> allSubjects = new ArrayList<String>();

        // test getAllSubjects with no existing data
        assertEquals(allSubjects, confluentClient.getAllSubjects());

        try {
            registerAndVerifySchema(confluentClient, getBadSchema(), 1, subject1);
            Assertions.fail("Registering bad schema should fail with " + ErrorCode.INVALID_SCHEMA.value());
        } catch (RestClientException rce) {
            assertEquals(ErrorCode.INVALID_SCHEMA.value(), rce.getErrorCode());
        }

        try {
            registerAndVerifySchema(confluentClient, getRandomJsonSchemas(1).get(0),
                    List.of(new SchemaReference("bad", "bad", 100)), 1, subject1);
            Assertions.fail("Registering bad reference should fail with " + ErrorCode.INVALID_SCHEMA.value());
        } catch (RestClientException rce) {
            assertEquals(ErrorCode.INVALID_SCHEMA.value(), rce.getErrorCode());
        }

        // test getAllSubjects with existing data
        assertEquals(allSubjects, confluentClient.getAllSubjects());
    }

    @Test
    public void testIncompatibleSchema() throws Exception {
        String subject = "testSubject";

        // Make two incompatible schemas - field 'myField2' has different types
        String schema1String = "{"
                + "\"$schema\": \"http://json-schema.org/draft-07/schema#\","
                + "\"$id\": \"https://acme.com/referrer.json\","
                + "\"type\":\"object\",\"properties\":{"
                + "\"myField1\": {\"type\":\"string\"},"
                + "\"myField2\": {\"type\":\"number\"}"
                + "},\"additionalProperties\":false"
                + "}";

        RegisterSchemaRequest registerRequest = new RegisterSchemaRequest();
        registerRequest.setSchema(schema1String);
        registerRequest.setSchemaType(JsonSchema.TYPE);

        String schema2String = "{"
                + "\"$schema\": \"http://json-schema.org/draft-07/schema#\","
                + "\"$id\": \"https://acme.com/referrer.json\","
                + "\"type\":\"object\",\"properties\":{"
                + "\"myField1\": {\"type\":\"string\"},"
                + "\"myField2\": {\"type\":\"string\"}"
                + "},\"additionalProperties\":false"
                + "}";

        // ensure registering incompatible schemas will raise an error
        confluentClient.updateCompatibility(
                CompatibilityLevel.FULL.name, subject);

        // test that compatibility check for incompatible schema returns false and the appropriate
        // error response from Avro
        int idOfRegisteredSchema1Subject1 = confluentClient.registerSchema(registerRequest, subject, true);

        try {
            registerRequest.setSchema(schema2String);
            registerRequest.setSchemaType(JsonSchema.TYPE);
            confluentClient.registerSchema(registerRequest, subject, true);
            Assertions.fail("Registering incompatible schema should fail with "
                    + ErrorCode.INVALID_COMPATIBILITY_LEVEL);
        } catch (RestClientException e) {
            Assertions.assertFalse(e.getMessage().isEmpty());
        }

        List<String> response = confluentClient.testCompatibility(registerRequest, subject,
                String.valueOf(
                        idOfRegisteredSchema1Subject1),
                false,
                true);
        Assertions.assertFalse(response.isEmpty());
        Assertions.assertTrue(response.get(2).contains("oldSchemaVersion:"));
        Assertions.assertTrue(response.get(3).contains("oldSchema:"));
        Assertions.assertTrue(response.get(4).contains("compatibility:"));
    }


    public static void registerAndVerifySchema(
            RestService restService,
            String schemaString,
            int expectedId,
            String subject
    ) throws IOException, RestClientException {
        registerAndVerifySchema(
                restService, schemaString, Collections.emptyList(), expectedId, subject);
    }

    public static void registerAndVerifySchema(
            RestService restService,
            String schemaString,
            List<SchemaReference> references,
            int expectedId,
            String subject
    ) throws IOException, RestClientException {
        int registeredId = restService.registerSchema(
                schemaString,
                JsonSchema.TYPE,
                references,
                subject
        );
        assertEquals(expectedId, registeredId, "Registering a new schema should succeed");
        assertEquals(MAPPER.readTree(schemaString), MAPPER.readTree(restService.getId(expectedId).getSchemaString()), "Registered schema should be found");
    }

    public static List<String> getRandomJsonSchemas(int num) {
        List<String> schemas = new ArrayList<>();
        for (int i = 0; i < num; i++) {
            String schema = "{\"type\":\"object\",\"properties\":{\"f"
                    + random.nextInt(Integer.MAX_VALUE)
                    + "\":"
                    + "{\"type\":\"string\"}},\"additionalProperties\":false}";
            schemas.add(schema);
        }
        return schemas;
    }

    public static Map<String, String> getJsonSchemaWithReferences() {
        Map<String, String> schemas = new HashMap<>();
        String reference = "{\"type\":\"object\",\"additionalProperties\":false,\"definitions\":"
                + "{\"ExternalType\":{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"}},"
                + "\"additionalProperties\":false}}}";
        schemas.put("ref.json", new JsonSchema(reference).canonicalString());
        String schemaString = "{"
                + "\"$id\": \"https://acme.com/referrer.json\","
                + "\"$schema\": \"http://json-schema.org/draft-07/schema#\","
                + "\"type\":\"object\",\"properties\":{\"Ref\":"
                + "{\"$ref\":\"ref.json#/definitions/ExternalType\"}},\"additionalProperties\":false}";
        schemas.put("main.json", schemaString);
        return schemas;
    }

    public static String getBadSchema() {
        return "{\"type\":\"bad-object\",\"properties\":{\"f"
                + random.nextInt(Integer.MAX_VALUE)
                + "\":"
                + "{\"type\":\"string\"}},\"additionalProperties\":false}";
    }

    */

}
