/*
 * Copyright 2020 Confluent Inc.
 *
 * Licensed under the Confluent Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.confluent.io/confluent-community-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 *
 */

/*
 * This test class has been adapted from the equivalent from Confluent.
 */

package io.apicurio.registry.noprofile.ccompat.rest.v7;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.ccompat.rest.error.ErrorCode;
import io.apicurio.registry.rest.client.exception.ArtifactNotFoundException;
import io.confluent.kafka.schemaregistry.CompatibilityLevel;
import io.confluent.kafka.schemaregistry.ParsedSchema;
import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import io.confluent.kafka.schemaregistry.client.rest.RestService;
import io.confluent.kafka.schemaregistry.client.rest.entities.Metadata;
import io.confluent.kafka.schemaregistry.client.rest.entities.Rule;
import io.confluent.kafka.schemaregistry.client.rest.entities.RuleMode;
import io.confluent.kafka.schemaregistry.client.rest.entities.RuleSet;
import io.confluent.kafka.schemaregistry.client.rest.entities.Schema;
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaReference;
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaString;
import io.confluent.kafka.schemaregistry.client.rest.entities.SubjectVersion;
import io.confluent.kafka.schemaregistry.client.rest.entities.requests.RegisterSchemaRequest;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.avro.SchemaParseException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static io.confluent.kafka.schemaregistry.CompatibilityLevel.*;
import static java.net.HttpURLConnection.HTTP_CONFLICT;
import static org.junit.Assert.*;

@QuarkusTest
public class CCompatRestTest extends AbstractResourceTestBase {

    @AfterEach
    protected void afterEach() throws Exception {
        try {
            clientV2.deleteArtifactsInGroup(null);
        } catch (ArtifactNotFoundException ignored) {
        }
    }

    @Test
    public void testBasic() throws Exception {
        String subject1 = "testBasic1";
        String subject2 = "testBasic2";
        int schemasInSubject1 = 10;
        List<Integer> allVersionsInSubject1 = new ArrayList<Integer>();
        List<String> allSchemasInSubject1 = ConfluentTestUtils.getRandomCanonicalAvroString(schemasInSubject1);
        int schemasInSubject2 = 5;
        List<Integer> allVersionsInSubject2 = new ArrayList<Integer>();
        List<String> allSchemasInSubject2 = ConfluentTestUtils.getRandomCanonicalAvroString(schemasInSubject2);
        List<String> allSubjects = new ArrayList<String>();

        List<Integer> schemaIds = new ArrayList<>();

        // test getAllVersions with no existing data
        try {
            confluentClient.getAllVersions(subject1);
            fail("Getting all versions from non-existing subject1 should fail with " + ErrorCode.SUBJECT_NOT_FOUND.value() + " (subject not found)");
        } catch (RestClientException rce) {
            assertEquals("Should get a 404 status for non-existing subject", ErrorCode.SUBJECT_NOT_FOUND.value(), rce.getErrorCode());
        }

        // test getAllSubjects with no existing data
        assertEquals("Getting all subjects should return empty", allSubjects, confluentClient.getAllSubjects());

        // test registering and verifying new schemas in subject1
        for (int i = 0; i < schemasInSubject1; i++) {
            String schema = allSchemasInSubject1.get(i);
            int expectedVersion = i + 1;
            int schemaId = ConfluentTestUtils.registerAndVerifySchema(confluentClient, schema, subject1);
            schemaIds.add(schemaId);
            allVersionsInSubject1.add(expectedVersion);
        }

        allSubjects.add(subject1);

        // test re-registering existing schemas
        for (int i = 0; i < schemasInSubject1; i++) {
            int expectedId = i + 1;
            String schemaString = allSchemasInSubject1.get(i);
            int foundId = confluentClient.registerSchema(schemaString, subject1, true);
            assertTrue("Re-registering an existing schema should return the existing version", schemaIds.get(i) == foundId);
        }

        // test registering schemas in subject2
        for (int i = 0; i < schemasInSubject2; i++) {
            String schema = allSchemasInSubject2.get(i);
            int expectedVersion = i + 1;
            ConfluentTestUtils.registerAndVerifySchema(confluentClient, schema, subject2);
            allVersionsInSubject2.add(expectedVersion);
        }
        allSubjects.add(subject2);

        // test getAllVersions with existing data
        assertEquals("Getting all versions from subject1 should match all registered versions", allVersionsInSubject1, confluentClient.getAllVersions(subject1));
        assertEquals("Getting all versions from subject2 should match all registered versions", allVersionsInSubject2, confluentClient.getAllVersions(subject2));

        // test getAllSubjects with existing data
        assertEquals("Getting all subjects should match all registered subjects", allSubjects, confluentClient.getAllSubjects());
    }

    @Test
    public void testRegisterSameSchemaOnDifferentSubject() throws Exception {
        String schema = ConfluentTestUtils.getRandomCanonicalAvroString(1).get(0);
        int id1 = confluentClient.registerSchema(schema, "subject1", true);
        int id2 = confluentClient.registerSchema(schema, "subject2", true);
        assertEquals("Registering the same schema under different subjects should return the same id", id1, id2);
    }

    @Test
    public void testRegisterInvalidSchemaBadType() throws Exception {
        String subject = "testRegisterInvalidSchemaBadType";

        //Invalid Field Type 'str'
        String badSchemaString = "{\"type\":\"record\"," + "\"name\":\"myrecord\"," + "\"fields\":" + "[{\"type\":\"str\",\"name\":\"field1\"}]}";

        String expectedErrorMessage = null;
        try {
            new org.apache.avro.Schema.Parser().parse(badSchemaString);
            fail("Parsing invalid schema string should fail with SchemaParseException");
        } catch (SchemaParseException spe) {
            expectedErrorMessage = spe.getMessage();
        }

        try {
            confluentClient.registerSchema(badSchemaString, subject, true);
            fail("Registering schema with invalid field type should fail with " + ErrorCode.INVALID_SCHEMA.value() + " (invalid schema)");
        } catch (RestClientException rce) {
            assertEquals("Invalid schema", ErrorCode.INVALID_SCHEMA.value(), rce.getErrorCode());
            assertTrue("Verify error message verbosity", rce.getMessage().contains(expectedErrorMessage));
        }
    }

    @Test
    public void testRegisterInvalidSchemaBadReference() throws Exception {
        String subject = "testRegisterInvalidSchemaBadReference";

        //Invalid Reference
        SchemaReference invalidReference = new SchemaReference("invalid.schema", "badSubject", 1);
        String schemaString = "{\"type\":\"record\"," + "\"name\":\"myrecord\"," + "\"fields\":" + "[{\"type\":\"string\",\"name\":\"field1\"}]}";

        try {
            confluentClient.registerSchema(schemaString, "AVRO", Collections.singletonList(invalidReference), subject, true);
            fail("Registering schema with invalid reference should fail with " + ErrorCode.INVALID_SCHEMA.value() + " (invalid schema)");
        } catch (RestClientException rce) {
            assertEquals("Invalid schema", ErrorCode.INVALID_SCHEMA.value(), rce.getErrorCode());
        }
    }

    @Test
    public void testCompatibleSchemaLookupBySubject() throws Exception {
        String subject = "testSubject";
        int numRegisteredSchemas = 0;
        int numSchemas = 10;

        List<String> allSchemas = ConfluentTestUtils.getRandomCanonicalAvroString(numSchemas);

        confluentClient.registerSchema(allSchemas.get(0), subject);
        numRegisteredSchemas++;

        // test compatibility of this schema against the latest version under the subject
        String schema1 = allSchemas.get(0);
        boolean isCompatible = confluentClient.testCompatibility(schema1, subject, "latest").isEmpty();
        assertTrue("First schema registered should be compatible", isCompatible);

        for (int i = 0; i < numSchemas; i++) {
            // Test that compatibility check doesn't change the number of versions
            String schema = allSchemas.get(i);
            isCompatible = confluentClient.testCompatibility(schema, subject, "latest").isEmpty();
            ConfluentTestUtils.checkNumberOfVersions(confluentClient, numRegisteredSchemas, subject);
        }
    }

    @Test
    public void testIncompatibleSchemaLookupBySubject() throws Exception {
        String subject = "testSubject";

        // Make two incompatible schemas - field 'f' has different types
        String schema1String = "{\"type\":\"record\"," + "\"name\":\"myrecord\"," + "\"fields\":" + "[{\"type\":\"string\",\"name\":" + "\"f" + "\"}]}";
        String schema1 = new AvroSchema(schema1String).canonicalString();

        String schema2String = "{\"type\":\"record\"," + "\"name\":\"myrecord\"," + "\"fields\":" + "[{\"type\":\"int\",\"name\":" + "\"f" + "\"}]}";
        String schema2 = new AvroSchema(schema2String).canonicalString();

        // ensure registering incompatible schemas will raise an error
        confluentClient.updateCompatibility(CompatibilityLevel.FULL.name, subject);

        // test that compatibility check for incompatible schema returns false and the appropriate
        // error response from Avro
        confluentClient.registerSchema(schema1, subject);
        int versionOfRegisteredSchema = confluentClient.lookUpSubjectVersion(schema1, subject).getVersion();
        boolean isCompatible = confluentClient.testCompatibility(schema2, subject, String.valueOf(versionOfRegisteredSchema)).isEmpty();
        assertFalse("Schema should be incompatible with specified version", isCompatible);
    }

    @Test
    public void testIncompatibleSchemaBySubject() throws Exception {
        String subject = "testSubject";

        String schema1String = "{\"type\":\"record\"," + "\"name\":\"myrecord\"," + "\"fields\":" + "[{\"type\":\"string\",\"name\":\"f1\"},{\"type\":\"string\",\"name\":\"f2\"}]}";
        String schema1 = new AvroSchema(schema1String).canonicalString();

        String schema2String = "{\"type\":\"record\"," + "\"name\":\"myrecord\"," + "\"fields\":" + "[{\"type\":\"string\",\"name\":\"f1\"}]}";
        String schema2 = new AvroSchema(schema2String).canonicalString();

        String schema3String = "{\"type\":\"record\"," + "\"name\":\"myrecord\"," + "\"fields\":" + "[{\"type\":\"string\",\"name\":\"f1\"},{\"type\":\"string\",\"name\":\"f3\"}]}";
        String schema3 = new AvroSchema(schema3String).canonicalString();

        confluentClient.registerSchema(schema1, subject);
        confluentClient.registerSchema(schema2, subject);

        confluentClient.updateCompatibility(CompatibilityLevel.FORWARD_TRANSITIVE.name, subject);

        //schema3 is compatible with schema2, but not compatible with schema1
        boolean isCompatible = confluentClient.testCompatibility(schema3, subject, "latest").isEmpty();
        assertTrue("Schema is compatible with the latest version", isCompatible);
        isCompatible = confluentClient.testCompatibility(schema3, subject, null).isEmpty();
        assertFalse("Schema should be incompatible with FORWARD_TRANSITIVE setting", isCompatible);
        try {
            confluentClient.registerSchema(schema3String, subject);
            fail("Schema register should fail since schema is incompatible");
        } catch (RestClientException e) {
            assertEquals("Schema register should fail since schema is incompatible", HTTP_CONFLICT, e.getErrorCode());
            assertTrue(e.getMessage().length() > 0);
        }
    }

    @Test
    public void testSchemaRegistrationUnderDiffSubjects() throws Exception {
        String subject1 = "testSchemaRegistrationUnderDiffSubjects1";
        String subject2 = "testSchemaRegistrationUnderDiffSubjects2";

        // Make two incompatible schemas - field 'f' has different types
        String schemaString1 = "{\"type\":\"record\"," + "\"name\":\"myrecord\"," + "\"fields\":" + "[{\"type\":\"string\",\"name\":" + "\"f" + "\"}]}";
        String schema1 = new AvroSchema(schemaString1).canonicalString();

        String schemaString2 = "{\"type\":\"record\"," + "\"name\":\"myrecord\"," + "\"fields\":" + "[{\"type\":\"int\",\"name\":" + "\"foo" + "\"}]}";
        String schema2 = new AvroSchema(schemaString2).canonicalString();

        confluentClient.updateCompatibility(CompatibilityLevel.NONE.name, subject1);
        confluentClient.updateCompatibility(CompatibilityLevel.NONE.name, subject2);

        int idOfRegisteredSchema1Subject1 = confluentClient.registerSchema(schema1, subject1);
        int versionOfRegisteredSchema1Subject1 = confluentClient.lookUpSubjectVersion(schema1, subject1).getVersion();
        assertEquals("1st schema under subject1 should have version 1", 1, versionOfRegisteredSchema1Subject1);
        assertEquals("1st schema registered globally should have id 1", 1, idOfRegisteredSchema1Subject1);

        int idOfRegisteredSchema2Subject1 = confluentClient.registerSchema(schema2, subject1);
        int versionOfRegisteredSchema2Subject1 = confluentClient.lookUpSubjectVersion(schema2, subject1).getVersion();
        assertEquals("2nd schema under subject1 should have version 2", 2, versionOfRegisteredSchema2Subject1);
        assertEquals("2nd schema registered globally should have id 2", 2, idOfRegisteredSchema2Subject1);

        int idOfRegisteredSchema2Subject2 = confluentClient.registerSchema(schema2, subject2);
        int versionOfRegisteredSchema2Subject2 = confluentClient.lookUpSubjectVersion(schema2, subject2).getVersion();
        assertEquals("2nd schema under subject1 should still have version 1 as the first schema under subject2", 1, versionOfRegisteredSchema2Subject2);
        assertEquals("Since schema is globally registered but not under subject2, id should not change", 2, idOfRegisteredSchema2Subject2);
    }

    @Test
    public void testConfigDefaults() throws Exception {
        assertEquals("Default compatibility level should be none for this test instance", NONE.name, confluentClient.getConfig(null).getCompatibilityLevel());

        // change it to forward
        confluentClient.updateCompatibility(CompatibilityLevel.FORWARD.name, null);

        assertEquals("New compatibility level should be forward for this test instance", FORWARD.name, confluentClient.getConfig(null).getCompatibilityLevel());
    }

    @Test
    public void testNonExistentSubjectConfigChange() throws Exception {
        String subject = "testSubject";
        try {
            confluentClient.updateCompatibility(CompatibilityLevel.FORWARD.name, subject);
        } catch (RestClientException e) {
            fail("Changing config for an invalid subject should succeed");
        }
        assertEquals("New compatibility level for this subject should be forward", FORWARD.name, confluentClient.getConfig(subject).getCompatibilityLevel());
    }

    @Test
    public void testSubjectConfigChange() throws Exception {
        String subject = "testSubjectConfigChange";
        assertEquals("Default compatibility level should be none for this test instance", NONE.name, confluentClient.getConfig(null).getCompatibilityLevel());

        // change subject compatibility to forward
        confluentClient.updateCompatibility(CompatibilityLevel.FORWARD.name, subject);

        assertEquals("Global compatibility level should remain none for this test instance", NONE.name, confluentClient.getConfig(null).getCompatibilityLevel());

        assertEquals("New compatibility level for this subject should be forward", FORWARD.name, confluentClient.getConfig(subject).getCompatibilityLevel());
    }

    @Test
    public void testGlobalConfigChange() throws Exception {
        assertEquals("Default compatibility level should be none for this test instance", NONE.name, confluentClient.getConfig(null).getCompatibilityLevel());

        // change subject compatibility to forward
        confluentClient.updateCompatibility(CompatibilityLevel.FORWARD.name, null);
        assertEquals("New Global compatibility level should be forward", FORWARD.name, confluentClient.getConfig(null).getCompatibilityLevel());

        // change subject compatibility to backward
        confluentClient.updateCompatibility(BACKWARD.name, null);
        assertEquals("New Global compatibility level should be backward", BACKWARD.name, confluentClient.getConfig(null).getCompatibilityLevel());

        // delete Global compatibility
        confluentClient.deleteConfig(null);
        assertEquals("Global compatibility level should be reverted to none", NONE.name, confluentClient.getConfig(RestService.DEFAULT_REQUEST_PROPERTIES, null, true).getCompatibilityLevel());
    }

    @Test
    public void testGetSchemaNonExistingId() throws Exception {
        try {
            confluentClient.getId(100);
            fail("Schema lookup by missing id should fail with " + ErrorCode.SCHEMA_NOT_FOUND.value() + " (schema not found)");
        } catch (RestClientException rce) {
            // this is expected.
            assertEquals("Should get a 404 status for non-existing id", ErrorCode.SCHEMA_NOT_FOUND.value(), rce.getErrorCode());
        }
    }

    @Test
    public void testGetSchemaTypes() throws Exception {
        assertEquals(new HashSet<>(Arrays.asList("AVRO", "JSON", "PROTOBUF")), new HashSet<>(confluentClient.getSchemaTypes()));
    }

    @Test
    public void testListVersionsNonExistingSubject() throws Exception {
        try {
            confluentClient.getAllVersions("Invalid");
            fail("Getting all versions of missing subject should fail with " + ErrorCode.SUBJECT_NOT_FOUND.value() + " (subject not found)");
        } catch (RestClientException rce) {
            // this is expected.
            assertEquals("Should get a 404 status for non-existing subject", ErrorCode.SUBJECT_NOT_FOUND.value(), rce.getErrorCode());
        }
    }

    @Test
    public void testGetVersionNonExistentSubject() throws Exception {
        // test getVersion on a non-existing subject
        try {
            confluentClient.getVersion("non-existing-subject", 1);
            fail("Getting version of missing subject should fail with " + ErrorCode.SUBJECT_NOT_FOUND.value() + " (subject not found)");
        } catch (RestClientException e) {
            // this is expected.
            assertEquals("Unregistered subject shouldn't be found in getVersion()", ErrorCode.SUBJECT_NOT_FOUND.value(), e.getErrorCode());
        }
    }

    @Test
    public void testGetNonExistingVersion() throws Exception {
        // test getVersion on a non-existing version
        String schema = ConfluentTestUtils.getRandomCanonicalAvroString(1).get(0);
        String subject = "test";
        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schema, subject);
        try {
            confluentClient.getVersion(subject, 200);
            fail("Getting unregistered version should fail with " + ErrorCode.VERSION_NOT_FOUND.value() + " (version not found)");
        } catch (RestClientException e) {
            // this is expected.
            assertEquals("Unregistered version shouldn't be found", ErrorCode.VERSION_NOT_FOUND.value(), e.getErrorCode());
        }
    }

    @Test
    public void testGetInvalidVersion() throws Exception {
        // test getVersion on a non-existing version
        String schema = ConfluentTestUtils.getRandomCanonicalAvroString(1).get(0);
        String subject = "test";
        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schema, subject);
        try {
            confluentClient.getVersion(subject, 0);
            fail("Getting invalid version should fail with " + ErrorCode.INVALID_VERSION + " (invalid version)");
        } catch (RestClientException e) {
            // this is expected.
            assertEquals("Invalid version shouldn't be found", ErrorCode.VERSION_NOT_FOUND.value(), e.getErrorCode());
        }
    }

    @Test
    public void testGetVersion() throws Exception {
        List<String> schemas = ConfluentTestUtils.getRandomCanonicalAvroString(2);
        String subject = "test";
        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schemas.get(0), subject);
        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schemas.get(1), subject);

        assertEquals("Version 1 schema should match", schemas.get(0), confluentClient.getVersion(subject, 1).getSchema());

        assertEquals("Version 2 schema should match", schemas.get(1), confluentClient.getVersion(subject, 2).getSchema());
        assertEquals("Latest schema should be the same as version 2", schemas.get(1), confluentClient.getLatestVersion(subject).getSchema());
    }

    @Test
    public void testGetLatestVersionSchemaOnly() throws Exception {
        List<String> schemas = ConfluentTestUtils.getRandomCanonicalAvroString(2);
        String subject = "test";
        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schemas.get(0), subject);
        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schemas.get(1), subject);

        assertEquals("Latest schema should be the same as version 2", schemas.get(1), confluentClient.getLatestVersionSchemaOnly(subject));
    }

    @Test
    public void testGetVersionSchemaOnly() throws Exception {
        List<String> schemas = ConfluentTestUtils.getRandomCanonicalAvroString(1);
        String subject = "test";
        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schemas.get(0), subject);

        assertEquals("Retrieved schema should be the same as version 1", schemas.get(0), confluentClient.getVersionSchemaOnly(subject, 1));
    }

    @Test
    public void testSchemaReferences() throws Exception {
        List<String> schemas = ConfluentTestUtils.getAvroSchemaWithReferences();
        String subject = "testSchemaReferences";
        String referrer = "testSchemaReferencesReferer";

        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schemas.get(0), subject);

        RegisterSchemaRequest request = new RegisterSchemaRequest();
        request.setSchema(schemas.get(1));
        SchemaReference ref = new SchemaReference("otherns.Subrecord", subject, 1);
        request.setReferences(Collections.singletonList(ref));
        int registeredId = confluentClient.registerSchema(request, referrer, false);

        SchemaString schemaString = confluentClient.getId(registeredId);
        // the newly registered schema should be immediately readable on the leader
        assertEquals("Registered schema should be found", schemas.get(1), schemaString.getSchemaString());

        assertEquals("Schema references should be found", Collections.singletonList(ref), schemaString.getReferences());

        List<Integer> refs = confluentClient.getReferencedBy(subject, 1);
        assertEquals(registeredId, refs.get(0).intValue());

        try {
            confluentClient.deleteSchemaVersion(RestService.DEFAULT_REQUEST_PROPERTIES, subject, String.valueOf(1));
            fail("Deleting reference should fail with " + ErrorCode.REFERENCE_EXISTS.value());
        } catch (RestClientException rce) {
            assertEquals("Reference found", ErrorCode.REFERENCE_EXISTS.value(), rce.getErrorCode());
        }

        assertEquals((Integer) 1, confluentClient.deleteSchemaVersion(RestService.DEFAULT_REQUEST_PROPERTIES, referrer, "1"));

        assertEquals((Integer) 1, confluentClient.deleteSchemaVersion(RestService.DEFAULT_REQUEST_PROPERTIES, subject, "1"));
    }

    @Test
    public void testSchemaReferencesMultipleLevels() throws Exception {
        String root = "[\"myavro.BudgetDecreased\",\"myavro.BudgetUpdated\"]";

        String ref1 = "{\n" + "  \"type\" : \"record\",\n" + "  \"name\" : \"BudgetDecreased\",\n" + "  \"namespace\" : \"myavro\",\n" + "  \"fields\" : [ {\n" + "    \"name\" : \"buyerId\",\n" + "    \"type\" : \"long\"\n" + "  }, {\n" + "    \"name\" : \"currency\",\n" + "    \"type\" : {\n" + "      \"type\" : \"myavro.currencies.Currency\"" + "    }\n" + "  }, {\n" + "    \"name\" : \"amount\",\n" + "    \"type\" : \"double\"\n" + "  } ]\n" + "}";

        String ref2 = "{\n" + "  \"type\" : \"record\",\n" + "  \"name\" : \"BudgetUpdated\",\n" + "  \"namespace\" : \"myavro\",\n" + "  \"fields\" : [ {\n" + "    \"name\" : \"buyerId\",\n" + "    \"type\" : \"long\"\n" + "  }, {\n" + "    \"name\" : \"currency\",\n" + "    \"type\" : {\n" + "      \"type\" : \"myavro.currencies.Currency\"" + "    }\n" + "  }, {\n" + "    \"name\" : \"updatedValue\",\n" + "    \"type\" : \"double\"\n" + "  } ]\n" + "}";

        String sharedRef = "{\n" + "      \"type\" : \"enum\",\n" + "      \"name\" : \"Currency\",\n" + "      \"namespace\" : \"myavro.currencies\",\n" + "      \"symbols\" : [ \"EUR\", \"USD\" ]\n" + "    }\n";

        ConfluentTestUtils.registerAndVerifySchema(confluentClient, new AvroSchema(sharedRef).canonicalString(), "shared");

        RegisterSchemaRequest request = new RegisterSchemaRequest();
        request.setSchema(ref1);
        SchemaReference ref = new SchemaReference("myavro.currencies.Currency", "shared", 1);
        request.setReferences(Collections.singletonList(ref));
        confluentClient.registerSchema(request, "ref1", false);

        request = new RegisterSchemaRequest();
        request.setSchema(ref2);
        ref = new SchemaReference("myavro.currencies.Currency", "shared", 1);
        request.setReferences(Collections.singletonList(ref));
        confluentClient.registerSchema(request, "ref2", false);

        request = new RegisterSchemaRequest();
        request.setSchema(root);
        SchemaReference r1 = new SchemaReference("myavro.BudgetDecreased", "ref1", 1);
        SchemaReference r2 = new SchemaReference("myavro.BudgetUpdated", "ref2", 1);
        request.setReferences(Arrays.asList(r1, r2));
        int registeredSchema = confluentClient.registerSchema(request, "root", false);

        SchemaString schemaString = confluentClient.getId(registeredSchema);
        // the newly registered schema should be immediately readable on the leader
        assertEquals("Registered schema should be found", root, schemaString.getSchemaString());

        assertEquals("Schema references should be found", Arrays.asList(r1, r2), schemaString.getReferences());
    }

    @Test
    public void testSchemaMissingReferences() throws Exception {
        List<String> schemas = ConfluentTestUtils.getAvroSchemaWithReferences();

        RegisterSchemaRequest request = new RegisterSchemaRequest();
        request.setSchema(schemas.get(1));
        request.setReferences(Collections.emptyList());

        Assertions.assertThrows(RestClientException.class, () -> {
            confluentClient.registerSchema(request, "referrer", false);
        });
    }

    @Test
    public void testSchemaNormalization() throws Exception {
        String subject1 = "testSchemaNormalization";

        String reference1 = "{\"type\":\"record\"," + "\"name\":\"Subrecord1\"," + "\"namespace\":\"otherns\"," + "\"fields\":" + "[{\"name\":\"field1\",\"type\":\"string\"}]}";
        ConfluentTestUtils.registerAndVerifySchema(confluentClient, reference1, "ref1");
        String reference2 = "{\"type\":\"record\"," + "\"name\":\"Subrecord2\"," + "\"namespace\":\"otherns\"," + "\"fields\":" + "[{\"name\":\"field2\",\"type\":\"string\"}]}";
        ConfluentTestUtils.registerAndVerifySchema(confluentClient, reference2, "ref2");

        SchemaReference ref1 = new SchemaReference("otherns.Subrecord1", "ref1", 1);
        SchemaReference ref2 = new SchemaReference("otherns.Subrecord2", "ref2", 1);

        // Two versions of same schema
        String schemaString1 = "{\"type\":\"record\"," + "\"name\":\"myrecord\"," + "\"fields\":" + "[{\"type\":{\"type\":\"int\"},\"name\":\"field0" + "\"}," + "{\"name\":\"field1\",\"type\":\"otherns.Subrecord1\"}," + "{\"name\":\"field2\",\"type\":\"otherns.Subrecord2\"}" + "]," + "\"extraMetadata\": {\"a\": 1, \"b\": 2}" + "}";
        String schemaString2 = "{\"type\":\"record\"," + "\"name\":\"myrecord\"," + "\"fields\":" + "[{\"type\":\"int\",\"name\":\"field0" + "\"}," + "{\"name\":\"field1\",\"type\":\"otherns.Subrecord1\"}," + "{\"name\":\"field2\",\"type\":\"otherns.Subrecord2\"}" + "]," + "\"extraMetadata\": {\"a\": 1, \"b\": 2}" + "}";

        RegisterSchemaRequest registerRequest = new RegisterSchemaRequest();
        registerRequest.setSchema(schemaString1);
        registerRequest.setReferences(Arrays.asList(ref1, ref2));
        int idOfRegisteredSchema1Subject1 = confluentClient.registerSchema(registerRequest, subject1, true);
        RegisterSchemaRequest lookUpRequest = new RegisterSchemaRequest();
        lookUpRequest.setSchema(schemaString2);
        lookUpRequest.setReferences(Arrays.asList(ref2, ref1));
        int versionOfRegisteredSchema1Subject1 = confluentClient.lookUpSubjectVersion(lookUpRequest, subject1, true, false).getVersion();
        assertEquals("1st schema under subject1 should have version 1", 1, versionOfRegisteredSchema1Subject1);

        // send schema with all references resolved
        lookUpRequest = new RegisterSchemaRequest();
        org.apache.avro.Schema.Parser parser = new org.apache.avro.Schema.Parser();
        parser.parse(reference1);
        parser.parse(reference2);
        AvroSchema resolvedSchema = new AvroSchema(parser.parse(schemaString2));
        lookUpRequest.setSchema(resolvedSchema.canonicalString());
        versionOfRegisteredSchema1Subject1 = confluentClient.lookUpSubjectVersion(lookUpRequest, subject1, true, false).getVersion();
        assertEquals("1st schema under subject1 should have version 1", 1, versionOfRegisteredSchema1Subject1);


        String recordInvalidDefaultSchema = "{\"namespace\": \"namespace\",\n" + " \"type\": \"record\",\n" + " \"name\": \"test\",\n" + " \"fields\": [\n" + "     {\"name\": \"string_default\", \"type\": \"string\", \"default\": null}\n" + "]\n" + "}";
        registerRequest = new RegisterSchemaRequest();
        registerRequest.setSchema(recordInvalidDefaultSchema);
        try {
            confluentClient.registerSchema(registerRequest, subject1, true);
            fail("Registering bad schema should fail with " + ErrorCode.INVALID_SCHEMA.value());
        } catch (RestClientException rce) {
            assertEquals("Invalid schema", ErrorCode.INVALID_SCHEMA.value(), rce.getErrorCode());
        }
    }

    @Test
    public void testBad() throws Exception {
        String subject1 = "testBad";
        List<String> allSubjects = new ArrayList<String>();

        // test getAllSubjects with no existing data
        assertEquals("Getting all subjects should return empty", allSubjects, confluentClient.getAllSubjects());

        try {
            ConfluentTestUtils.registerAndVerifySchema(confluentClient, ConfluentTestUtils.getBadSchema(), subject1);
            fail("Registering bad schema should fail with " + ErrorCode.INVALID_SCHEMA.value());
        } catch (RestClientException rce) {
            assertEquals("Invalid schema", ErrorCode.INVALID_SCHEMA.value(), rce.getErrorCode());
        }

        try {
            ConfluentTestUtils.registerAndVerifySchema(confluentClient, ConfluentTestUtils.getRandomCanonicalAvroString(1).get(0), Arrays.asList(new SchemaReference("bad", "bad", 100)), subject1);
            fail("Registering bad reference should fail with " + ErrorCode.INVALID_SCHEMA.value());
        } catch (RestClientException rce) {
            assertEquals("Invalid schema", ErrorCode.INVALID_SCHEMA.value(), rce.getErrorCode());
        }

        // test getAllSubjects with existing data
        assertEquals("Getting all subjects should match all registered subjects", allSubjects, confluentClient.getAllSubjects());
    }

    @Test
    public void testLookUpSchemaUnderNonExistentSubject() throws Exception {
        String schema = ConfluentTestUtils.getRandomCanonicalAvroString(1).get(0);
        try {
            confluentClient.lookUpSubjectVersion(schema, "non-existent-subject");
            fail("Looking up schema under missing subject should fail with " + ErrorCode.SUBJECT_NOT_FOUND.value() + " (subject not found)");
        } catch (RestClientException rce) {
            assertEquals("Subject not found", ErrorCode.SUBJECT_NOT_FOUND.value(), rce.getErrorCode());
        }
    }

    @Test
    public void testLookUpNonExistentSchemaUnderSubject() throws Exception {
        String subject = "test";
        List<String> schemas = ConfluentTestUtils.getRandomCanonicalAvroString(2);
        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schemas.get(0), subject);
        confluentClient.updateCompatibility(CompatibilityLevel.NONE.name, subject);

        try {
            confluentClient.lookUpSubjectVersion(schemas.get(1), subject);
            fail("Looking up missing schema under subject should fail with " + ErrorCode.SCHEMA_NOT_FOUND.value() + " (schema not found)");
        } catch (RestClientException rce) {
            assertEquals("Schema not found", ErrorCode.SCHEMA_NOT_FOUND.value(), rce.getErrorCode());
        }
    }

    @Test
    public void testGetVersionsAssociatedWithSchemaId() throws Exception {
        String subject1 = "testGetVersionsAssociatedWithSchemaId1";
        String subject2 = "testGetVersionsAssociatedWithSchemaId2";

        String schema = ConfluentTestUtils.getRandomCanonicalAvroString(1).get(0);
        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schema, subject1);
        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schema, subject2);

        List<SubjectVersion> associatedSubjects = confluentClient.getAllVersionsById(1);
        assertEquals(associatedSubjects.size(), 2);
        assertTrue(associatedSubjects.contains(new SubjectVersion(subject1, 1)));
        assertTrue(associatedSubjects.contains(new SubjectVersion(subject2, 1)));

        assertEquals("Deleting Schema Version Success", (Integer) 1, confluentClient.deleteSchemaVersion(RestService.DEFAULT_REQUEST_PROPERTIES, subject2, "1"));

        associatedSubjects = confluentClient.getAllVersionsById(1);
        assertEquals(associatedSubjects.size(), 1);
        assertTrue(associatedSubjects.contains(new SubjectVersion(subject1, 1)));

        associatedSubjects = confluentClient.getAllVersionsById(RestService.DEFAULT_REQUEST_PROPERTIES, 1, null, true);
        assertEquals(associatedSubjects.size(), 2);
        assertTrue(associatedSubjects.contains(new SubjectVersion(subject1, 1)));
        assertTrue(associatedSubjects.contains(new SubjectVersion(subject2, 1)));
    }

    @Test
    public void testCompatibilityNonExistentSubject() throws Exception {
        String schema = ConfluentTestUtils.getRandomCanonicalAvroString(1).get(0);
        boolean result = confluentClient.testCompatibility(schema, "non-existent-subject", "latest").isEmpty();
        assertTrue("Compatibility succeeds", result);

        result = confluentClient.testCompatibility(schema, "non-existent-subject", null).isEmpty();
        assertTrue("Compatibility succeeds", result);
    }

    @Test
    public void testCompatibilityNonExistentVersion() throws Exception {
        String schema = ConfluentTestUtils.getRandomCanonicalAvroString(1).get(0);
        String subject = "testCompatibilityNonExistentVersion";
        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schema, subject);
        try {
            confluentClient.testCompatibility(schema, subject, "100");
            fail("Testing compatibility for missing version should fail with " + ErrorCode.VERSION_NOT_FOUND.value() + " (version not found)");
        } catch (RestClientException rce) {
            assertEquals("Version not found", ErrorCode.VERSION_NOT_FOUND.value(), rce.getErrorCode());
        }
    }

    @Test
    public void testCompatibilityInvalidVersion() throws Exception {
        String schema = ConfluentTestUtils.getRandomCanonicalAvroString(1).get(0);
        String subject = "test";
        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schema, subject);
        try {
            confluentClient.testCompatibility(schema, subject, "earliest");
            fail("Testing compatibility for invalid version should fail with " + ErrorCode.VERSION_NOT_FOUND.value() + " (version not found)");
        } catch (RestClientException rce) {
            assertEquals("Version not found", ErrorCode.VERSION_NOT_FOUND.value(), rce.getErrorCode());
        }
    }

    @Test
    public void testGetConfigNonExistentSubject() throws Exception {
        try {
            confluentClient.getConfig("non-existent-subject");
            fail("Getting the configuration of a missing subject should fail with " + ErrorCode.SUBJECT_NOT_FOUND.value() + " error code (subject not found)");
        } catch (RestClientException rce) {
            assertEquals("Subject not found", ErrorCode.SUBJECT_NOT_FOUND.value(), rce.getErrorCode());
        }
    }

    @Test
    public void testCanonicalization() throws Exception {
        // schema string with extra white space
        String schema = "{   \"type\":   \"string\"}";
        String subject = "test";

        int id = confluentClient.registerSchema(schema, subject);

        assertEquals("Registering the same schema should get back the same id", id, confluentClient.registerSchema(schema, subject));

        assertEquals("Lookup the same schema should get back the same id", id, confluentClient.lookUpSubjectVersion(schema, subject).getId().intValue());
    }

    @Test
    public void testDeleteSchemaVersionBasic() throws Exception {
        List<String> schemas = ConfluentTestUtils.getRandomCanonicalAvroString(2);
        String subject = "testDeleteSchemaVersionBasic";

        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schemas.get(0), subject);
        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schemas.get(1), subject);

        assertEquals("Deleting Schema Version Success", (Integer) 2, confluentClient.deleteSchemaVersion(RestService.DEFAULT_REQUEST_PROPERTIES, subject, "2"));

        assertEquals(Collections.singletonList(1), confluentClient.getAllVersions(subject));

        try {
            confluentClient.getVersion(subject, 2);
            fail(String.format("Getting Version %s for subject %s should fail with %s", "2", subject, ErrorCode.VERSION_NOT_FOUND.value()));
        } catch (RestClientException rce) {
            assertEquals("Version not found", ErrorCode.VERSION_NOT_FOUND.value(), rce.getErrorCode());
        }
        try {
            RegisterSchemaRequest request = new RegisterSchemaRequest();
            request.setSchema(schemas.get(1));
            confluentClient.lookUpSubjectVersion(RestService.DEFAULT_REQUEST_PROPERTIES, request, subject, false, false);
            fail(String.format("Lookup Subject Version %s for subject %s should fail with %s", "2", subject, ErrorCode.SCHEMA_NOT_FOUND.value()));
        } catch (RestClientException rce) {
            assertEquals("Schema not found", ErrorCode.SCHEMA_NOT_FOUND.value(), rce.getErrorCode());
        }

        assertEquals("Deleting Schema Version Success", (Integer) 1, confluentClient.deleteSchemaVersion(RestService.DEFAULT_REQUEST_PROPERTIES, subject, "latest"));
        try {
            List<Integer> versions = confluentClient.getAllVersions(subject);
            fail("Getting all versions from non-existing subject1 should fail with " + ErrorCode.SUBJECT_NOT_FOUND.value() + " (subject not found). Got " + versions);
        } catch (RestClientException rce) {
            assertEquals("Should get a 404 status for non-existing subject", ErrorCode.SUBJECT_NOT_FOUND.value(), rce.getErrorCode());
        }

        //re-register twice and versions should be same
        for (int i = 0; i < 2; i++) {
            ConfluentTestUtils.registerAndVerifySchema(confluentClient, schemas.get(0), subject);
            assertEquals(Collections.singletonList(3), confluentClient.getAllVersions(subject));
        }

    }

    @Test
    public void testDeleteSchemaVersionPermanent() throws Exception {
        List<String> schemas = ConfluentTestUtils.getRandomCanonicalAvroString(2);
        String subject = "test";

        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schemas.get(0), subject);
        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schemas.get(1), subject);

        //permanent delete without soft delete first
        try {
            confluentClient.deleteSchemaVersion(RestService.DEFAULT_REQUEST_PROPERTIES, subject, "2", true);
            fail(String.format("Permanent deleting first time should throw schemaVersionNotSoftDeletedException"));
        } catch (RestClientException rce) {
            assertEquals("Schema version must be soft deleted first", ErrorCode.SCHEMA_VERSION_NOT_SOFT_DELETED, rce.getErrorCode());
        }

        //soft delete
        assertEquals("Deleting Schema Version Success", (Integer) 2, confluentClient.deleteSchemaVersion(RestService.DEFAULT_REQUEST_PROPERTIES, subject, "2"));

        assertEquals(Collections.singletonList(1), confluentClient.getAllVersions(subject));
        assertEquals(Arrays.asList(1, 2), confluentClient.getAllVersions(RestService.DEFAULT_REQUEST_PROPERTIES, subject, true));
        //soft delete again
        try {
            confluentClient.deleteSchemaVersion(RestService.DEFAULT_REQUEST_PROPERTIES, subject, "2");
            fail(String.format("Soft deleting second time should throw schemaVersionSoftDeletedException"));
        } catch (RestClientException rce) {
            assertEquals("Schema version already soft deleted", ErrorCode.SCHEMA_VERSION_SOFT_DELETED, rce.getErrorCode());
        }

        try {
            confluentClient.getVersion(subject, 2);
            fail(String.format("Getting Version %s for subject %s should fail with %s", "2", subject, ErrorCode.VERSION_NOT_FOUND.value()));
        } catch (RestClientException rce) {
            assertEquals("Version not found", ErrorCode.VERSION_NOT_FOUND.value(), rce.getErrorCode());
        }

        Schema schema = confluentClient.getVersion(subject, 2, true);
        assertEquals("Lookup Version Match", (Integer) 2, schema.getVersion());

        try {
            RegisterSchemaRequest request = new RegisterSchemaRequest();
            request.setSchema(schemas.get(1));
            confluentClient.lookUpSubjectVersion(RestService.DEFAULT_REQUEST_PROPERTIES, request, subject, false, false);
            fail(String.format("Lookup Subject Version %s for subject %s should fail with %s", "2", subject, ErrorCode.SCHEMA_NOT_FOUND.value()));
        } catch (RestClientException rce) {
            assertEquals("Schema not found", ErrorCode.SCHEMA_NOT_FOUND.value(), rce.getErrorCode());
        }
        // permanent delete
        assertEquals("Deleting Schema Version Success", (Integer) 2, confluentClient.deleteSchemaVersion(RestService.DEFAULT_REQUEST_PROPERTIES, subject, "2", true));
        // GET after permanent delete should give exception
        try {
            confluentClient.getVersion(subject, 2, true);
            fail(String.format("Getting Version %s for subject %s should fail with %s", "2", subject, ErrorCode.VERSION_NOT_FOUND.value()));
        } catch (RestClientException rce) {
            assertEquals("Version not found", ErrorCode.VERSION_NOT_FOUND.value(), rce.getErrorCode());
        }
        //permanent delete again
        try {
            confluentClient.deleteSchemaVersion(RestService.DEFAULT_REQUEST_PROPERTIES, subject, "2", true);
            fail(String.format("Getting Version %s for subject %s should fail with %s", "2", subject, ErrorCode.VERSION_NOT_FOUND.value()));
        } catch (RestClientException rce) {
            assertEquals("Version not found", ErrorCode.VERSION_NOT_FOUND.value(), rce.getErrorCode());
        }

        assertEquals("Deleting Schema Version Success", (Integer) 1, confluentClient.deleteSchemaVersion(RestService.DEFAULT_REQUEST_PROPERTIES, subject, "latest"));

        try {
            List<Integer> versions = confluentClient.getAllVersions(subject);
            fail("Getting all versions from non-existing subject1 should fail with " + ErrorCode.SUBJECT_NOT_FOUND.value() + " (subject not found). Got " + versions);
        } catch (RestClientException rce) {
            assertEquals("Should get a 404 status for non-existing subject", ErrorCode.SUBJECT_NOT_FOUND.value(), rce.getErrorCode());
        }

        //re-register twice and versions should be same
        //after permanent delete of 2, the new version coming up will be 2
        for (int i = 0; i < 2; i++) {
            ConfluentTestUtils.registerAndVerifySchema(confluentClient, schemas.get(0), subject);
            assertEquals(Collections.singletonList(2), confluentClient.getAllVersions(subject));
        }

    }

    @Test
    public void testDeleteSchemaVersionInvalidSubject() throws Exception {
        try {
            String subject = "testDeleteSchemaVersionInvalidSubject";
            confluentClient.deleteSchemaVersion(RestService.DEFAULT_REQUEST_PROPERTIES, subject, "1");
            fail("Deleting a non existent subject version should fail with " + ErrorCode.SUBJECT_NOT_FOUND.value() + " error code (subject not found)");
        } catch (RestClientException rce) {
            assertEquals("Subject not found", ErrorCode.SUBJECT_NOT_FOUND.value(), rce.getErrorCode());
        }
    }

    @Test
    public void testDeleteLatestVersion() throws Exception {
        List<String> schemas = ConfluentTestUtils.getRandomCanonicalAvroString(3);
        String subject = "testDeleteLatestVersion";

        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schemas.get(0), subject);
        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schemas.get(1), subject);

        assertEquals("Deleting Schema Version Success", (Integer) 2, confluentClient.deleteSchemaVersion(RestService.DEFAULT_REQUEST_PROPERTIES, subject, "latest"));

        Schema schema = confluentClient.getLatestVersion(subject);
        assertEquals("Latest Version Schema", schemas.get(0), schema.getSchema());

        assertEquals("Deleting Schema Version Success", (Integer) 1, confluentClient.deleteSchemaVersion(RestService.DEFAULT_REQUEST_PROPERTIES, subject, "latest"));
        try {
            confluentClient.getLatestVersion(subject);
            fail("Getting latest versions from non-existing subject should fail with " + ErrorCode.SUBJECT_NOT_FOUND.value() + " (subject not found).");
        } catch (RestClientException rce) {
            assertEquals("Should get a 404 status for non-existing subject", ErrorCode.SUBJECT_NOT_FOUND.value(), rce.getErrorCode());
        }

        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schemas.get(2), subject);
        assertEquals("Latest version available after subject re-registration", schemas.get(2), confluentClient.getLatestVersion(subject).getSchema());
    }

    @Test
    public void testGetLatestVersionNonExistentSubject() throws Exception {
        String subject = "non_existent_subject";

        try {
            confluentClient.getLatestVersion(subject);
            fail("Getting latest versions from non-existing subject should fail with " + ErrorCode.SUBJECT_NOT_FOUND.value() + " (subject not found).");
        } catch (RestClientException rce) {
            assertEquals("Should get a 404 status for non-existing subject", ErrorCode.SUBJECT_NOT_FOUND.value(), rce.getErrorCode());
        }
    }

    @Test
    public void testGetLatestVersionDeleteOlder() throws Exception {
        List<String> schemas = ConfluentTestUtils.getRandomCanonicalAvroString(2);
        String subject = "test";

        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schemas.get(0), subject);
        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schemas.get(1), subject);

        assertEquals("Latest Version Schema", schemas.get(1), confluentClient.getLatestVersion(subject).getSchema());

        assertEquals("Deleting Schema Older Version Success", (Integer) 1, confluentClient.deleteSchemaVersion(RestService.DEFAULT_REQUEST_PROPERTIES, subject, "1"));
        assertEquals("Latest Version Schema Still Same", schemas.get(1), confluentClient.getLatestVersion(subject).getSchema());
    }

    @Test
    public void testDeleteInvalidVersion() throws Exception {
        List<String> schemas = ConfluentTestUtils.getRandomCanonicalAvroString(1);
        String subject = "test";

        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schemas.get(0), subject);
        try {
            confluentClient.deleteSchemaVersion(RestService.DEFAULT_REQUEST_PROPERTIES, subject, "2");
        } catch (RestClientException rce) {
            assertEquals("Should get a 404 status for non-existing subject version", ErrorCode.VERSION_NOT_FOUND.value(), rce.getErrorCode());
        }

    }

    @Test
    public void testDeleteWithLookup() throws Exception {
        List<String> schemas = ConfluentTestUtils.getRandomCanonicalAvroString(2);
        String subject = "testDeleteWithLookup";

        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schemas.get(0), subject);
        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schemas.get(1), subject);
        assertEquals("Deleting Schema Version Success", (Integer) 1, confluentClient.deleteSchemaVersion(RestService.DEFAULT_REQUEST_PROPERTIES, subject, "1"));
        try {
            confluentClient.lookUpSubjectVersion(schemas.get(0), subject, false);
            fail(String.format("Lookup Subject Version %s for subject %s should fail with %s", "2", subject, ErrorCode.SCHEMA_NOT_FOUND.value()));
        } catch (RestClientException rce) {
            assertEquals("Schema not found", ErrorCode.SCHEMA_NOT_FOUND.value(), rce.getErrorCode());
        }
        //verify deleted schema
        Schema schema = confluentClient.lookUpSubjectVersion(schemas.get(0), subject, true);
        assertEquals("Lookup Version Match", (Integer) 1, schema.getVersion());

        //re-register schema again and verify we get latest version
        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schemas.get(0), subject);
        schema = confluentClient.lookUpSubjectVersion(schemas.get(0), subject, true);
        assertEquals("Lookup Version Match", (Integer) 1, schema.getVersion());
        schema = confluentClient.lookUpSubjectVersion(schemas.get(1), subject, false);
        assertEquals("Lookup Version Match", (Integer) 2, schema.getVersion());
    }

    @Test
    public void testIncompatibleSchemaLookupBySubjectAfterDelete() throws Exception {
        String subject = "testIncompatibleSchemaLookupBySubjectAfterDelete";

        // Make two incompatible schemas - field 'g' has different types
        String schema1String = "{\"type\":\"record\"," + "\"name\":\"myrecord\"," + "\"fields\":" + "[{\"type\":\"string\",\"name\":" + "\"f" + "\"}]}";
        String schema1 = new AvroSchema(schema1String).canonicalString();

        String wrongSchema2String = "{\"type\":\"record\"," + "\"name\":\"myrecord\"," + "\"fields\":" + "[{\"type\":\"string\",\"name\":" + "\"f" + "\"}," + "{\"type\":\"string\",\"name\":" + "\"g\" , \"default\":\"d\"}" + "]}";
        String wrongSchema2 = new AvroSchema(wrongSchema2String).canonicalString();

        String correctSchema2String = "{\"type\":\"record\"," + "\"name\":\"myrecord\"," + "\"fields\":" + "[{\"type\":\"string\",\"name\":" + "\"f" + "\"}," + "{\"type\":\"int\",\"name\":" + "\"g\" , \"default\":0}" + "]}";
        String correctSchema2 = new AvroSchema(correctSchema2String).canonicalString();
        // ensure registering incompatible schemas will raise an error
        confluentClient.updateCompatibility(CompatibilityLevel.BACKWARD.name, subject);

        // test that compatibility check for incompatible schema returns false and the appropriate
        // error response from Avro
        confluentClient.registerSchema(schema1, subject, true);

        boolean isCompatible = confluentClient.testCompatibility(wrongSchema2, subject, "latest").isEmpty();
        assertTrue("Schema should be compatible with specified version", isCompatible);

        confluentClient.registerSchema(wrongSchema2, subject, true);

        isCompatible = confluentClient.testCompatibility(correctSchema2, subject, "latest").isEmpty();
        assertFalse("Schema should be incompatible with specified version", isCompatible);
        try {
            confluentClient.registerSchema(correctSchema2, subject);
            fail("Schema should be Incompatible");
        } catch (RestClientException rce) {
            assertEquals("Incompatible Schema", HTTP_CONFLICT, rce.getErrorCode());
        }

        confluentClient.deleteSchemaVersion(RestService.DEFAULT_REQUEST_PROPERTIES, subject, "latest");
        isCompatible = confluentClient.testCompatibility(correctSchema2, subject, "latest").isEmpty();
        assertTrue("Schema should be compatible with specified version", isCompatible);

        confluentClient.registerSchema(correctSchema2, subject, true);

        assertEquals("Version is same", (Integer) 3, confluentClient.lookUpSubjectVersion(correctSchema2String, subject, true, false).getVersion());

    }

    @Test
    public void testSubjectCompatibilityAfterDeletingAllVersions() throws Exception {
        String subject = "testSubjectCompatibilityAfterDeletingAllVersions";

        String schema1String = "{\"type\":\"record\"," + "\"name\":\"myrecord\"," + "\"fields\":" + "[{\"type\":\"string\",\"name\":" + "\"f" + "\"}]}";
        String schema1 = new AvroSchema(schema1String).canonicalString();

        String schema2String = "{\"type\":\"record\"," + "\"name\":\"myrecord\"," + "\"fields\":" + "[{\"type\":\"string\",\"name\":" + "\"f" + "\"}," + "{\"type\":\"string\",\"name\":" + "\"g\" , \"default\":\"d\"}" + "]}";
        String schema2 = new AvroSchema(schema2String).canonicalString();

        confluentClient.updateCompatibility(CompatibilityLevel.FULL.name, null);
        confluentClient.updateCompatibility(CompatibilityLevel.BACKWARD.name, subject);

        confluentClient.registerSchema(schema1, subject, true);
        confluentClient.registerSchema(schema2, subject);

        confluentClient.deleteSchemaVersion(RestService.DEFAULT_REQUEST_PROPERTIES, subject, "1");
        assertEquals("Compatibility Level Exists", CompatibilityLevel.BACKWARD.name, confluentClient.getConfig(subject).getCompatibilityLevel());
        assertEquals("Top Compatibility Level Exists", CompatibilityLevel.FULL.name, confluentClient.getConfig(null).getCompatibilityLevel());
        confluentClient.deleteSchemaVersion(RestService.DEFAULT_REQUEST_PROPERTIES, subject, "2");
        try {
            confluentClient.getConfig(subject);
        } catch (RestClientException rce) {
            assertEquals("Compatibility Level doesn't exist", ErrorCode.SUBJECT_COMPATIBILITY_NOT_CONFIGURED, rce.getErrorCode());
        }
        assertEquals("Top Compatibility Level Exists", CompatibilityLevel.FULL.name, confluentClient.getConfig(null).getCompatibilityLevel());

    }

    @Test
    public void testListSubjects() throws Exception {
        List<String> schemas = ConfluentTestUtils.getRandomCanonicalAvroString(2);
        String subject1 = "test1";
        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schemas.get(0), subject1);
        String subject2 = "test2";
        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schemas.get(1), subject2);
        ;
        List<String> expectedResponse = new ArrayList<>();
        expectedResponse.add(subject1);
        expectedResponse.add(subject2);
        assertEquals("Current Subjects", expectedResponse, confluentClient.getAllSubjects());
        List<Integer> deletedResponse = new ArrayList<>();
        deletedResponse.add(1);
        assertEquals("Versions Deleted Match", deletedResponse, confluentClient.deleteSubject(RestService.DEFAULT_REQUEST_PROPERTIES, subject2));

        expectedResponse = new ArrayList<>();
        expectedResponse.add(subject1);
        assertEquals("Current Subjects", expectedResponse, confluentClient.getAllSubjects());

        expectedResponse = new ArrayList<>();
        expectedResponse.add(subject1);
        expectedResponse.add(subject2);
        assertEquals("Current Subjects", expectedResponse, confluentClient.getAllSubjects(true));

        assertEquals("Versions Deleted Match", deletedResponse, confluentClient.deleteSubject(RestService.DEFAULT_REQUEST_PROPERTIES, subject2, true));

        expectedResponse = new ArrayList<>();
        expectedResponse.add(subject1);
        assertEquals("Current Subjects", expectedResponse, confluentClient.getAllSubjects());
    }

    @Test
    public void testListSoftDeletedSubjectsAndSchemas() throws Exception {
        List<String> schemas = ConfluentTestUtils.getRandomCanonicalAvroString(3);
        String subject1 = "test1";
        String subject2 = "test2";
        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schemas.get(0), subject1);
        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schemas.get(1), subject1);
        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schemas.get(2), subject2);

        assertEquals((Integer) 1, confluentClient.deleteSchemaVersion(RestService.DEFAULT_REQUEST_PROPERTIES, subject1, "1"));
        assertEquals((Integer) 1, confluentClient.deleteSchemaVersion(RestService.DEFAULT_REQUEST_PROPERTIES, subject2, "1"));

        assertEquals("List All Versions Match", Collections.singletonList(2), confluentClient.getAllVersions(subject1));
        assertEquals("List All Versions Include deleted Match", Arrays.asList(1, 2), confluentClient.getAllVersions(RestService.DEFAULT_REQUEST_PROPERTIES, subject1, true));

        assertEquals("List All Subjects Match", Collections.singletonList(subject1), confluentClient.getAllSubjects());
        assertEquals("List All Subjects Include deleted Match", Arrays.asList(subject1, subject2), confluentClient.getAllSubjects(true));
    }

    @Test
    public void testDeleteSubjectBasic() throws Exception {
        List<String> schemas = ConfluentTestUtils.getRandomCanonicalAvroString(2);
        String subject = "testDeleteSubjectBasic";
        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schemas.get(0), subject);
        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schemas.get(1), subject);
        List<Integer> expectedResponse = new ArrayList<>();
        expectedResponse.add(1);
        expectedResponse.add(2);
        assertEquals("Versions Deleted Match", expectedResponse, confluentClient.deleteSubject(RestService.DEFAULT_REQUEST_PROPERTIES, subject));
        try {
            confluentClient.getLatestVersion(subject);
            fail(String.format("Subject %s should not be found", subject));
        } catch (RestClientException rce) {
            assertEquals("Subject Not Found", ErrorCode.SUBJECT_NOT_FOUND.value(), rce.getErrorCode());
        }

    }

    @Test
    public void testDeleteSubjectException() throws Exception {
        List<String> schemas = ConfluentTestUtils.getRandomCanonicalAvroString(2);
        String subject = "testDeleteSubjectException";
        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schemas.get(0), subject);
        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schemas.get(1), subject);
        List<Integer> expectedResponse = new ArrayList<>();
        expectedResponse.add(1);
        expectedResponse.add(2);
        assertEquals("Versions Deleted Match", expectedResponse, confluentClient.deleteSubject(RestService.DEFAULT_REQUEST_PROPERTIES, subject));

        Schema schema = confluentClient.lookUpSubjectVersion(schemas.get(0), subject, true);
        assertEquals(1, (long) schema.getVersion());
        schema = confluentClient.lookUpSubjectVersion(schemas.get(1), subject, true);
        assertEquals(2, (long) schema.getVersion());

        try {
            confluentClient.deleteSubject(RestService.DEFAULT_REQUEST_PROPERTIES, subject);
            fail(String.format("Subject %s should not be found", subject));
        } catch (RestClientException rce) {
            assertEquals("Subject exists in soft deleted format.", ErrorCode.SUBJECT_SOFT_DELETED.value(), rce.getErrorCode());
        }
    }


    @Test
    public void testDeleteSubjectPermanent() throws Exception {
        List<String> schemas = ConfluentTestUtils.getRandomCanonicalAvroString(2);
        String subject = "testDeleteSubjectPermanent";
        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schemas.get(0), subject);
        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schemas.get(1), subject);
        List<Integer> expectedResponse = new ArrayList<>();
        expectedResponse.add(1);
        expectedResponse.add(2);

        try {
            confluentClient.deleteSubject(RestService.DEFAULT_REQUEST_PROPERTIES, subject, true);
            fail(String.format("Delete permanent should not succeed"));
        } catch (RestClientException rce) {
            assertEquals("Subject '%s' was not deleted first before permanent delete", ErrorCode.SUBJECT_NOT_SOFT_DELETED.value(), rce.getErrorCode());
        }

        assertEquals("Versions Deleted Match", expectedResponse, confluentClient.deleteSubject(RestService.DEFAULT_REQUEST_PROPERTIES, subject));

        Schema schema = confluentClient.lookUpSubjectVersion(schemas.get(0), subject, true);
        assertEquals(1, (long) schema.getVersion());
        schema = confluentClient.lookUpSubjectVersion(schemas.get(1), subject, true);
        assertEquals(2, (long) schema.getVersion());

        assertEquals("Versions Deleted Match", expectedResponse, confluentClient.deleteSubject(RestService.DEFAULT_REQUEST_PROPERTIES, subject, true));
        for (Integer i : expectedResponse) {
            try {
                confluentClient.lookUpSubjectVersion(schemas.get(i - i), subject, false);
                fail(String.format("Subject %s should not be found", subject));
            } catch (RestClientException rce) {
                assertEquals("Subject Not Found", ErrorCode.SUBJECT_NOT_FOUND.value(), rce.getErrorCode());
            }

            try {
                confluentClient.lookUpSubjectVersion(schemas.get(i - 1), subject, true);
                fail(String.format("Subject %s should not be found", subject));
            } catch (RestClientException rce) {
                assertEquals("Subject Not Found", ErrorCode.SUBJECT_NOT_FOUND.value(), rce.getErrorCode());
            }
        }
    }

    @Test
    public void testSubjectCompatibilityAfterDeletingSubject() throws Exception {
        String subject = "testSubjectCompatibilityAfterDeletingSubject";

        String schema1String = "{\"type\":\"record\"," + "\"name\":\"myrecord\"," + "\"fields\":" + "[{\"type\":\"string\",\"name\":" + "\"f" + "\"}]}";
        String schema1 = new AvroSchema(schema1String).canonicalString();

        String schema2String = "{\"type\":\"record\"," + "\"name\":\"myrecord\"," + "\"fields\":" + "[{\"type\":\"string\",\"name\":" + "\"f" + "\"}," + "{\"type\":\"string\",\"name\":" + "\"g\" , \"default\":\"d\"}" + "]}";
        String schema2 = new AvroSchema(schema2String).canonicalString();

        confluentClient.updateCompatibility(CompatibilityLevel.FULL.name, null);
        confluentClient.updateCompatibility(CompatibilityLevel.BACKWARD.name, subject);

        confluentClient.registerSchema(schema1, subject, true);
        confluentClient.registerSchema(schema2, subject, true);

        confluentClient.deleteSubject(RestService.DEFAULT_REQUEST_PROPERTIES, subject);
        try {
            confluentClient.getConfig(subject);
        } catch (RestClientException rce) {
            assertEquals("Compatibility Level doesn't exist", ErrorCode.SUBJECT_COMPATIBILITY_NOT_CONFIGURED, rce.getErrorCode());
        }
        assertEquals("Top Compatibility Level Exists", CompatibilityLevel.FULL.name, confluentClient.getConfig(null).getCompatibilityLevel());

    }

    @Test
    public void testRegisterWithAndWithoutMetadata() throws Exception {
        String subject = "testSubject";

        ParsedSchema schema1 = new AvroSchema("{\"type\":\"record\"," + "\"name\":\"myrecord\"," + "\"fields\":" + "[{\"type\":\"string\",\"name\":\"f1\"}]}");

        Map<String, String> properties = Collections.singletonMap("application.version", "2");
        Metadata metadata = new Metadata(null, properties, null);
        RegisterSchemaRequest request1 = new RegisterSchemaRequest(schema1);
        request1.setMetadata(metadata);

        int id = confluentClient.registerSchema(request1, subject, false);

        RegisterSchemaRequest request2 = new RegisterSchemaRequest(schema1);
        int id2 = confluentClient.registerSchema(request2, subject, false);
        assertEquals(id, id2);
    }

    @Test
    public void testRegisterDropsRuleSet() throws Exception {
        String subject = "testRegisterDropsRuleSet";

        ParsedSchema schema1 = new AvroSchema("{\"type\":\"record\"," + "\"name\":\"myrecord\"," + "\"fields\":" + "[{\"type\":\"string\",\"name\":\"f1\"}]}");

        Rule r1 = new Rule("foo", null, null, RuleMode.READ, "ENCRYPT", null, null, null, null, null, false);
        List<Rule> rules = Collections.singletonList(r1);
        RuleSet ruleSet = new RuleSet(null, rules);
        RegisterSchemaRequest request1 = new RegisterSchemaRequest(schema1);
        request1.setRuleSet(ruleSet);
        int id = confluentClient.registerSchema(request1, subject, false);

        SchemaString schemaString = confluentClient.getId(id, subject);
        assertNull(schemaString.getRuleSet());
    }

    private String matchHeaderValue(Map<String, List<String>> responseHeaders, String headerName, String expectedHeaderValue) {
        if (responseHeaders.isEmpty() || responseHeaders.get(headerName) == null) return null;

        return responseHeaders.get(headerName).stream().filter(value -> expectedHeaderValue.equals(value.trim())).findAny().orElse(null);
    }

    private String buildRequestUrl(String baseUrl, String path) {
        // Join base URL and path, collapsing any duplicate forward slash delimiters
        return baseUrl.replaceFirst("/$", "") + "/" + path.replaceFirst("^/", "");
    }
}
