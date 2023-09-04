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

package io.apicurio.registry.noprofile.ccompat.rest.v7.protobuf;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.ccompat.rest.error.ErrorCode;
import io.confluent.kafka.schemaregistry.CompatibilityLevel;
import io.confluent.kafka.schemaregistry.client.rest.RestService;
import io.confluent.kafka.schemaregistry.client.rest.entities.Schema;
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaReference;
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaString;
import io.confluent.kafka.schemaregistry.client.rest.entities.requests.RegisterSchemaRequest;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchema;
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchemaUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.Assert.*;

@QuarkusTest
public class ProtobufCcompatRestTest extends AbstractResourceTestBase {

    private static String META_SCHEMA = "syntax = \"proto3\";\n" +
            "package confluent;\n" +
            "\n" +
            "import \"google/protobuf/descriptor.proto\";\n" +
            "\n" +
            "option java_package = \"io.confluent.protobuf\";\n" +
            "option java_outer_classname = \"MetaProto\";\n" +
            "\n" +
            "message Meta {\n" +
            "  string doc = 1;\n" +
            "  map<string, string> params = 2;\n" +
            "  repeated string tags = 3;\n" +
            "}\n" +
            "\n" +
            "extend google.protobuf.FileOptions {\n" +
            "  Meta file_meta = 1088;\n" +
            "}\n" +
            "extend google.protobuf.MessageOptions {\n" +
            "  Meta message_meta = 1088;\n" +
            "}\n" +
            "extend google.protobuf.FieldOptions {\n" +
            "  Meta field_meta = 1088;\n" +
            "}\n" +
            "extend google.protobuf.EnumOptions {\n" +
            "  Meta enum_meta = 1088;\n" +
            "}\n" +
            "extend google.protobuf.EnumValueOptions {\n" +
            "  Meta enum_value_meta = 1088;\n" +
            "}\n";

    private static final Random random = new Random();

    @Test
    public void testBasic() throws Exception {
        String subject1 = "testBasic1";
        String subject2 = "testBasic2";
        int schemasInSubject1 = 10;
        List<Integer> allVersionsInSubject1 = new ArrayList<Integer>();
        List<String> allSchemasInSubject1 = getRandomProtobufSchemas(schemasInSubject1);
        int schemasInSubject2 = 5;
        List<Integer> allVersionsInSubject2 = new ArrayList<Integer>();
        List<String> allSchemasInSubject2 = getRandomProtobufSchemas(schemasInSubject2);
        List<String> allSubjects = new ArrayList<String>();

        // test getAllSubjects with no existing data
        assertEquals("Getting all subjects should return empty",
                allSubjects,
                confluentClient.getAllSubjects()
        );

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
                    ProtobufSchema.TYPE,
                    Collections.emptyList(),
                    subject1
            );
            assertEquals("Re-registering an existing schema should return the existing version",
                    expectedId,
                    foundId
            );
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
        assertEquals(
                "Getting all versions from subject1 should match all registered versions",
                allVersionsInSubject1,
                confluentClient.getAllVersions(subject1)
        );
        assertEquals(
                "Getting all versions from subject2 should match all registered versions",
                allVersionsInSubject2,
                confluentClient.getAllVersions(subject2)
        );

        // test getAllSubjects with existing data
        assertEquals("Getting all subjects should match all registered subjects",
                allSubjects,
                confluentClient.getAllSubjects()
        );
    }

    @Test
    public void testSchemaReferences() throws Exception {
        Map<String, String> schemas = getProtobufSchemaWithDependencies();
        String subject = "confluent/meta.proto";

        confluentClient.registerSchema(schemas.get("confluent/meta.proto"),
                ProtobufSchema.TYPE,
                Collections.emptyList(),
                subject);

        subject = "reference";

        confluentClient.registerSchema(schemas.get("ref.proto"),
                ProtobufSchema.TYPE,
                Collections.emptyList(),
                subject);

        RegisterSchemaRequest request = new RegisterSchemaRequest();
        request.setSchema(schemas.get("root.proto"));
        request.setSchemaType(ProtobufSchema.TYPE);
        SchemaReference ref = new SchemaReference("ref.proto", "reference", 1);
        SchemaReference meta = new SchemaReference("confluent/meta.proto", "confluent/meta.proto", 1);
        List<SchemaReference> refs = Arrays.asList(ref, meta);
        request.setReferences(refs);

        int registeredSchemaId = confluentClient.registerSchema(request, "referrer", false);

        SchemaString schemaString = confluentClient.getId(registeredSchemaId);
        // the newly registered schema should be immediately readable on the leader
        assertEquals("Registered schema should be found",
                schemas.get("root.proto"),
                schemaString.getSchemaString()
        );

        assertEquals("Schema dependencies should be found",
                refs,
                schemaString.getReferences()
        );

        Root.ReferrerMessage referrer = Root.ReferrerMessage.newBuilder().build();
        ProtobufSchema schema = ProtobufSchemaUtils.getSchema(referrer);
        schema = schema.copy(refs);
        Schema registeredSchema = confluentClient.lookUpSubjectVersion(schema.canonicalString(),
                ProtobufSchema.TYPE, schema.references(), "referrer", false);
    }

    @Test
    public void testSchemaReferencesPkg() throws Exception {
        String msg1 = "syntax = \"proto3\";\n" +
                "package pkg1;\n" +
                "\n" +
                "option go_package = \"pkg1pb\";\n" +
                "option java_multiple_files = true;\n" +
                "option java_outer_classname = \"Msg1Proto\";\n" +
                "option java_package = \"com.pkg1\";\n" +
                "\n" +
                "message Message1 {\n" +
                "  string s = 1;\n" +
                "}\n";
        String subject = "pkg1/msg1.proto";
        RegisterSchemaRequest request = new RegisterSchemaRequest();
        request.setSchema(msg1);
        request.setSchemaType(ProtobufSchema.TYPE);
        confluentClient.registerSchema(request, subject, false);
        subject = "pkg2/msg2.proto";
        String msg2 = "syntax = \"proto3\";\n" +
                "package pkg2;\n" +
                "\n" +
                "option go_package = \"pkg2pb\";\n" +
                "option java_multiple_files = true;\n" +
                "option java_outer_classname = \"Msg2Proto\";\n" +
                "option java_package = \"com.pkg2\";\n" +
                "\n" +
                "import \"pkg1/msg1.proto\";\n" +
                "\n" +
                "message Message2 {\n" +
                "  map<string, pkg1.Message1> map = 1;\n" +
                "  pkg1.Message1 f2 = 2;\n" +
                "}\n";
        request = new RegisterSchemaRequest();
        request.setSchema(msg2);
        request.setSchemaType(ProtobufSchema.TYPE);
        SchemaReference meta = new SchemaReference("pkg1/msg1.proto", "pkg1/msg1.proto", 1);
        List<SchemaReference> refs = Arrays.asList(meta);
        request.setReferences(refs);
        int registeredId = confluentClient.registerSchema(request, subject, false);
    }

    @Test
    public void testSchemaMissingReferences() throws Exception {
        Map<String, String> schemas = getProtobufSchemaWithDependencies();

        RegisterSchemaRequest request = new RegisterSchemaRequest();
        request.setSchema(schemas.get("root.proto"));
        request.setSchemaType(ProtobufSchema.TYPE);
        request.setReferences(Collections.emptyList());
        Assertions.assertThrows(RestClientException.class, () -> confluentClient.registerSchema(request, "testSchemaMissingReferences", false));
    }

    @Test
    public void testIncompatibleSchema() throws Exception {
        String subject = "testIncompatibleSchema";

        // Make two incompatible schemas - field 'myField2' has different types
        String schema1String = "syntax = \"proto3\";\n" +
                "package pkg3;\n" +
                "\n" +
                "message Schema1 {\n" +
                "  string f1 = 1;\n" +
                "  string f2 = 2;\n" +
                "}\n";

        RegisterSchemaRequest registerRequest = new RegisterSchemaRequest();
        registerRequest.setSchema(schema1String);
        registerRequest.setSchemaType(ProtobufSchema.TYPE);

        String schema2String = "syntax = \"proto3\";\n" +
                "package pkg3;\n" +
                "\n" +
                "message Schema1 {\n" +
                "  string f1 = 1;\n" +
                "  int32 f2 = 2;\n" +
                "}\n";

        // ensure registering incompatible schemas will raise an error
        confluentClient.updateCompatibility(
                CompatibilityLevel.FULL.name, subject);

        // test that compatibility check for incompatible schema returns false and the appropriate
        // error response from Avro
        int idOfRegisteredSchema1Subject1 = confluentClient.registerSchema(registerRequest, subject, true);

        try {
            registerRequest.setSchema(schema2String);
            registerRequest.setSchemaType(ProtobufSchema.TYPE);
            confluentClient.registerSchema(registerRequest, subject, true);
            fail("Registering incompatible schema should fail with "
                    + ErrorCode.INVALID_SCHEMA);
        } catch (RestClientException e) {
            assertTrue(e.getMessage().length() > 0);
            assertTrue(e.getMessage().contains("oldSchemaVersion:"));
            assertTrue(e.getMessage().contains("oldSchema:"));
            assertTrue(e.getMessage().contains("compatibility:"));
        }

        List<String> response = confluentClient.testCompatibility(registerRequest, subject,
                String.valueOf(
                        idOfRegisteredSchema1Subject1),
                false,
                true);
        assertTrue(response.size() > 0);
        assertTrue(response.get(2).contains("oldSchemaVersion:"));
        assertTrue(response.get(3).contains("oldSchema:"));
        assertTrue(response.get(4).contains("compatibility:"));
    }

    @Test
    public void testSchemaNormalization() throws Exception {
        String subject1 = "testSchemaNormalization";

        String msg1 = "syntax = \"proto3\";\n" +
                "package pkg1;\n" +
                "\n" +
                "option go_package = \"pkg1pb\";\n" +
                "option java_multiple_files = true;\n" +
                "option java_outer_classname = \"Msg1Proto\";\n" +
                "option java_package = \"com.pkg1\";\n" +
                "\n" +
                "message Message1 {\n" +
                "  string s = 1;\n" +
                "}\n";

        String subject = "pkg1/msg1.proto";


        confluentClient.registerSchema(msg1,
                ProtobufSchema.TYPE,
                Collections.emptyList(),
                subject);

        String msg2 = "syntax = \"proto3\";\n" +
                "package pkg2;\n" +
                "\n" +
                "option go_package = \"pkg2pb\";\n" +
                "option java_multiple_files = true;\n" +
                "option java_outer_classname = \"Msg2Proto\";\n" +
                "option java_package = \"com.pkg2\";\n" +
                "\n" +
                "\n" +
                "message Message2 {\n" +
                "  string s = 1;\n" +
                "}\n";

        subject = "pkg2/msg2.proto";

        confluentClient.registerSchema(msg2,
                ProtobufSchema.TYPE,
                Collections.emptyList(),
                subject);

        String msg3 = "syntax = \"proto3\";\n" +
                "package pkg3;\n" +
                "\n" +
                "option go_package = \"pkg3pb\";\n" +
                "option java_multiple_files = true;\n" +
                "option java_outer_classname = \"Msg3Proto\";\n" +
                "option java_package = \"com.pkg3\";\n" +
                "\n" +
                "import \"pkg1/msg1.proto\";\n" +
                "import \"pkg2/msg2.proto\";\n" +
                "\n" +
                "message Message3 {\n" +
                "  map<string, pkg1.Message1> map = 1;\n" +
                "  pkg1.Message1 f1 = 2;\n" +
                "  pkg2.Message2 f2 = 3;\n" +
                "}\n";

        RegisterSchemaRequest request = new RegisterSchemaRequest();
        request.setSchema(msg3);
        request.setSchemaType(ProtobufSchema.TYPE);
        SchemaReference ref1 = new SchemaReference("pkg1/msg1.proto", "pkg1/msg1.proto", 1);
        SchemaReference ref2 = new SchemaReference("pkg2/msg2.proto", "pkg2/msg2.proto", 1);
        List<SchemaReference> refs = Arrays.asList(ref1, ref2);
        request.setReferences(refs);

        int registeredId = confluentClient.registerSchema(request, subject1, true);
    }

    @Test
    public void testBad() throws Exception {
        String subject1 = "testBad";
        List<String> allSubjects = new ArrayList<String>();

        // test getAllSubjects with no existing data
        assertEquals("Getting all subjects should return empty",
                allSubjects,
                confluentClient.getAllSubjects()
        );

        try {
            registerAndVerifySchema(confluentClient, getBadSchema(), 1, subject1);
            fail("Registering bad schema should fail with " + ErrorCode.INVALID_SCHEMA);
        } catch (RestClientException rce) {
            assertEquals("Invalid schema",
                    ErrorCode.INVALID_SCHEMA.value(),
                    rce.getErrorCode());
        }

        try {
            registerAndVerifySchema(confluentClient, getRandomProtobufSchemas(1).get(0),
                    Arrays.asList(new SchemaReference("bad", "bad", 100)), 1, subject1);
            fail("Registering bad reference should fail with " + ErrorCode.INVALID_SCHEMA);
        } catch (RestClientException rce) {
            assertEquals("Invalid schema",
                    ErrorCode.INVALID_SCHEMA.value(),
                    rce.getErrorCode());
        }

        // test getAllSubjects with existing data
        assertEquals("Getting all subjects should match all registered subjects",
                allSubjects,
                confluentClient.getAllSubjects()
        );
    }

    @Test
    public void testCustomOption() throws Exception {
        String subject = "testCustomOption";
        String enumOptionSchemaString = "syntax = \"proto3\";\n"
                + "\n"
                + "import \"google/protobuf/descriptor.proto\";\n"
                + "\n"
                + "option java_package = \"io.confluent.kafka.serializers.protobuf.test\";\n"
                + "option java_outer_classname = \"TestEnumProtos\";\n"
                + "option php_namespace = \"Bug\\\\V1\";\n"
                + "\n"
                + "message TestEnum {\n"
                + "  option (some_ref) = \"https://test.com\";\n"
                + "\n"
                + "  Suit suit = 1;\n"
                + "\n"
                + "  oneof test_oneof {\n"
                + "    option (some_ref) = \"https://test.com\";\n"
                + "  \n"
                + "    string name = 2;\n"
                + "    int32 age = 3;\n"
                + "  }\n"
                + "\n"
                + "  enum Suit {\n"
                + "    option (some_ref) = \"https://test.com\";\n"
                + "    SPADES = 0;\n"
                + "    HEARTS = 1;\n"
                + "    DIAMONDS = 2;\n"
                + "    CLUBS = 3;\n"
                + "  }\n"
                + "}\n";

        registerAndVerifySchema(confluentClient, enumOptionSchemaString, 1, subject);
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
        int registeredId = restService.registerSchema(schemaString,
                ProtobufSchema.TYPE,
                references,
                subject
        );
        Assertions.assertEquals(
                (long) expectedId,
                (long) registeredId,
                "Registering a new schema should succeed"
        );
        Assertions.assertEquals(
                schemaString.trim(),
                restService.getId(expectedId).getSchemaString().trim(),
                "Registered schema should be found"
        );
    }

    public static List<String> getRandomProtobufSchemas(int num) {
        List<String> schemas = new ArrayList<>();
        for (int i = 0; i < num; i++) {
            String schema =
                    "syntax = \"proto3\";\npackage io.confluent.kafka.serializers.protobuf.test;\n\n"
                            + "message MyMessage {\n  string f"
                            + random.nextInt(Integer.MAX_VALUE)
                            + " = 1;\n  bool is_active = 2;\n}\n";
            schemas.add(schema);
        }
        return schemas;
    }

    public static Map<String, String> getProtobufSchemaWithDependencies() {
        Map<String, String> schemas = new HashMap<>();
        String meta = META_SCHEMA;
        schemas.put("confluent/meta.proto", meta);
        String reference =
                "syntax = \"proto3\";\npackage io.confluent.kafka.serializers.protobuf.test;\n\n"
                        + "message ReferencedMessage {\n  string ref_id = 1;\n  bool is_active = 2;\n}\n";
        schemas.put("ref.proto", reference);
        String schemaString = "syntax = \"proto3\";\n"
                + "package io.confluent.kafka.serializers.protobuf.test;\n"
                + "\n"
                + "import \"ref.proto\";\n"
                + "import \"confluent/meta.proto\";\n"
                + "\n"
                + "message ReferrerMessage {\n"
                + "  option (confluent.message_meta) = {\n"
                + "    doc: \"ReferrerMessage\"\n"
                + "  };\n"
                + "\n"
                + "  string root_id = 1;\n"
                + "  .io.confluent.kafka.serializers.protobuf.test.ReferencedMessage ref = 2 [(confluent.field_meta) = {\n"
                + "    doc: \"ReferencedMessage\"\n"
                + "  }];\n"
                + "}\n";
        schemas.put("root.proto", schemaString);
        return schemas;
    }

    public static String getBadSchema() {
        String schema =
                "syntax = \"proto3\";\npackage io.confluent.kafka.serializers.protobuf.test;\n\n"
                        + "bad-message MyMessage {\n  string f"
                        + random.nextInt(Integer.MAX_VALUE)
                        + " = 1;\n  bool is_active = 2;\n}\n";
        return schema;
    }
}

