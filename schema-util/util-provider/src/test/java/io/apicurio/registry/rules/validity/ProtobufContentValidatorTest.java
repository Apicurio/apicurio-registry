package io.apicurio.registry.rules.validity;

import com.google.protobuf.DescriptorProtos;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.protobuf.rules.validity.ProtobufContentValidator;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.types.ContentTypes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tests the Protobuf content validator.
 */
public class ProtobufContentValidatorTest extends ArtifactUtilProviderTestBase {

    @Test
    public void testValidProtobufSchema() throws Exception {
        TypedContent content = resourceToTypedContentHandle("protobuf-valid.proto");
        ProtobufContentValidator validator = new ProtobufContentValidator();
        validator.validate(ValidityLevel.SYNTAX_ONLY, content, Collections.emptyMap());
    }

    @Test
    public void testInvalidProtobufSchema() throws Exception {
        TypedContent content = resourceToTypedContentHandle("protobuf-invalid.proto");
        ProtobufContentValidator validator = new ProtobufContentValidator();
        Assertions.assertThrows(RuleViolationException.class, () -> {
            validator.validate(ValidityLevel.SYNTAX_ONLY, content, Collections.emptyMap());
        });
    }

    @Test
    public void testValidateProtobufWithImports() throws Exception {
        TypedContent mode = resourceToTypedContentHandle("mode.proto");
        TypedContent tableInfo = resourceToTypedContentHandle("table_info.proto");
        ProtobufContentValidator validator = new ProtobufContentValidator();
        validator.validate(ValidityLevel.SYNTAX_ONLY, tableInfo,
                Collections.singletonMap("sample/mode.proto", mode));
    }

    @Test
    public void testDuplicateTagNumbers() throws Exception {
        TypedContent content = resourceToTypedContentHandle("protobuf-duplicate-tags.proto");
        ProtobufContentValidator validator = new ProtobufContentValidator();
        Assertions.assertThrows(RuleViolationException.class, () -> {
            validator.validate(ValidityLevel.SYNTAX_ONLY, content, Collections.emptyMap());
        });
    }

    @Test
    public void testNegativeTagNumber() throws Exception {
        TypedContent content = resourceToTypedContentHandle("protobuf-negative-tag.proto");
        ProtobufContentValidator validator = new ProtobufContentValidator();
        Assertions.assertThrows(RuleViolationException.class, () -> {
            validator.validate(ValidityLevel.SYNTAX_ONLY, content, Collections.emptyMap());
        });
    }

    @Test
    public void testInvalidFieldType() throws Exception {
        TypedContent content = resourceToTypedContentHandle("protobuf-invalid-type.proto");
        ProtobufContentValidator validator = new ProtobufContentValidator();
        Assertions.assertThrows(RuleViolationException.class, () -> {
            validator.validate(ValidityLevel.SYNTAX_ONLY, content, Collections.emptyMap());
        });
    }

    @Test
    public void testValidProtobufSchemaWithFullValidation() throws Exception {
        TypedContent content = resourceToTypedContentHandle("protobuf-valid.proto");
        ProtobufContentValidator validator = new ProtobufContentValidator();
        // Should not throw - valid schema passes semantic validation
        validator.validate(ValidityLevel.FULL, content, Collections.emptyMap());
    }

    @Test
    public void testDuplicateTagNumbersWithDependencies() throws Exception {
        TypedContent mode = resourceToTypedContentHandle("mode.proto");
        TypedContent content = resourceToTypedContentHandle("protobuf-duplicate-tags-with-import.proto");
        ProtobufContentValidator validator = new ProtobufContentValidator();
        Assertions.assertThrows(RuleViolationException.class, () -> {
            validator.validate(ValidityLevel.SYNTAX_ONLY, content,
                    Collections.singletonMap("sample/mode.proto", mode));
        });
    }

    @Test
    public void testValidProto2WithNestedTypes() throws Exception {
        // Test proto2 schema with nested message types, enums, and default values
        // This schema is similar to what integration tests use
        TypedContent content = resourceToTypedContentHandle("protobuf-tutorial.proto");
        ProtobufContentValidator validator = new ProtobufContentValidator();
        // Should not throw - valid proto2 schema with nested types
        validator.validate(ValidityLevel.FULL, content, Collections.emptyMap());
    }

    @Test
    public void testValidProto3UuidSchema() throws Exception {
        // Test proto3 schema similar to what serdes tests register
        TypedContent content = resourceToTypedContentHandle("protobuf-uuid-simple.proto");
        ProtobufContentValidator validator = new ProtobufContentValidator();
        // Should not throw - valid proto3 schema
        validator.validate(ValidityLevel.FULL, content, Collections.emptyMap());
    }

    @Test
    public void testRejectsUnsafeIdentifierInBinaryDescriptor() {
        // Regression for GHSA-xq3m-2v4x-88gg style identifier injection: a binary
        // FileDescriptorProto carrying an identifier outside the protobuf identifier
        // grammar must be rejected during validation.
        DescriptorProtos.DescriptorProto message = DescriptorProtos.DescriptorProto.newBuilder()
                .setName("Data(){console}")
                .build();
        DescriptorProtos.FileDescriptorProto fileDescriptor = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("malicious.proto")
                .setSyntax("proto3")
                .setPackage("poison")
                .addMessageType(message)
                .build();

        String base64Descriptor = Base64.getEncoder().encodeToString(fileDescriptor.toByteArray());
        TypedContent content = TypedContent.create(ContentHandle.create(base64Descriptor),
                ContentTypes.APPLICATION_PROTOBUF);

        ProtobufContentValidator validator = new ProtobufContentValidator();
        Assertions.assertThrows(RuleViolationException.class,
                () -> validator.validate(ValidityLevel.SYNTAX_ONLY, content, Collections.emptyMap()));
    }

    @Test
    public void testRejectsConflictingFqnAcrossReferences() {
        // GHSA-xq3m-2v4x-88gg threat model: a main artifact imports two references that
        // both define the same fully qualified name with different fields. Each reference
        // is individually valid, so protobuf-java's own symbol table accepts the build;
        // the registry must still reject the upload.
        String mainSchema = "syntax = \"proto3\";\n"
                + "package poison;\n"
                + "import \"refA.proto\";\n"
                + "import \"refB.proto\";\n"
                + "message Client { string id = 1; }\n";
        String refASchema = "syntax = \"proto3\";\n"
                + "package poison;\n"
                + "message Token { string id = 1; }\n";
        String refBSchema = "syntax = \"proto3\";\n"
                + "package poison;\n"
                + "message Token { bool admin = 1; }\n";

        TypedContent main = TypedContent.create(ContentHandle.create(mainSchema),
                ContentTypes.APPLICATION_PROTOBUF);
        Map<String, TypedContent> refs = new LinkedHashMap<>();
        refs.put("refA.proto", TypedContent.create(ContentHandle.create(refASchema),
                ContentTypes.APPLICATION_PROTOBUF));
        refs.put("refB.proto", TypedContent.create(ContentHandle.create(refBSchema),
                ContentTypes.APPLICATION_PROTOBUF));

        ProtobufContentValidator validator = new ProtobufContentValidator();
        RuleViolationException exception = Assertions.assertThrows(RuleViolationException.class,
                () -> validator.validate(ValidityLevel.SYNTAX_ONLY, main, refs));
        Assertions.assertTrue(exception.getMessage().contains("Conflicting Protobuf type definition"),
                "Expected conflict message, got: " + exception.getMessage());
        Assertions.assertTrue(exception.getMessage().contains("poison.Token"),
                "Expected FQN poison.Token in message, got: " + exception.getMessage());
    }

    @Test
    public void testAllowsIdenticalFqnAcrossReferences() throws Exception {
        // Re-declaring the same FQN across files with byte-identical descriptors is a
        // legitimate pattern (e.g. a schema and its own mirror) and must pass.
        String sharedMessage = "message Token { string id = 1; }";
        String refASchema = "syntax = \"proto3\";\npackage poison;\n" + sharedMessage + "\n";
        String refBSchema = "syntax = \"proto3\";\npackage poison;\n" + sharedMessage + "\n";
        String mainSchema = "syntax = \"proto3\";\n"
                + "package poison;\n"
                + "import \"refA.proto\";\n"
                + "message Client { string id = 1; }\n";

        TypedContent main = TypedContent.create(ContentHandle.create(mainSchema),
                ContentTypes.APPLICATION_PROTOBUF);
        Map<String, TypedContent> refs = new LinkedHashMap<>();
        refs.put("refA.proto", TypedContent.create(ContentHandle.create(refASchema),
                ContentTypes.APPLICATION_PROTOBUF));
        refs.put("refB.proto", TypedContent.create(ContentHandle.create(refBSchema),
                ContentTypes.APPLICATION_PROTOBUF));

        ProtobufContentValidator validator = new ProtobufContentValidator();
        validator.validate(ValidityLevel.SYNTAX_ONLY, main, refs);
    }

    @Test
    public void testAllowsIdenticalFqnWithOnlyCommentDifferences() throws Exception {
        // DescriptorProto comparison ignores comments by construction. Two references
        // declaring the same Token with identical wire shape but wildly different
        // documentation (line comments, block comments, inline comments, different
        // wording) must pass validation. This is a common real-world situation because
        // schema registries and protobuf tooling often strip or regenerate comments.
        String refASchema = "syntax = \"proto3\";\n"
                + "package poison;\n"
                + "// Token issued by service A\n"
                + "// multi-line\n"
                + "// header\n"
                + "message Token {\n"
                + "  string id = 1; // primary identifier\n"
                + "}\n";
        String refBSchema = "syntax = \"proto3\";\n"
                + "package poison;\n"
                + "/*\n"
                + " * Token issued by service B.\n"
                + " * This block comment is intentionally different from ref A.\n"
                + " */\n"
                + "message Token {\n"
                + "  /* field doc */ string id = 1;\n"
                + "}\n";
        String mainSchema = "syntax = \"proto3\";\n"
                + "package poison;\n"
                + "import \"refA.proto\";\n"
                + "import \"refB.proto\";\n"
                + "message Client { string id = 1; }\n";

        TypedContent main = TypedContent.create(ContentHandle.create(mainSchema),
                ContentTypes.APPLICATION_PROTOBUF);
        Map<String, TypedContent> refs = new LinkedHashMap<>();
        refs.put("refA.proto", TypedContent.create(ContentHandle.create(refASchema),
                ContentTypes.APPLICATION_PROTOBUF));
        refs.put("refB.proto", TypedContent.create(ContentHandle.create(refBSchema),
                ContentTypes.APPLICATION_PROTOBUF));

        ProtobufContentValidator validator = new ProtobufContentValidator();
        validator.validate(ValidityLevel.SYNTAX_ONLY, main, refs);
    }

    @Test
    public void testAllowsIdenticalFqnWithOnlyWhitespaceDifferences() throws Exception {
        // DescriptorProto comparison ignores whitespace by construction. One reference
        // uses compact single-line form, the other uses expanded multi-line form with
        // extra blank lines and mixed indentation. Both describe the same wire shape and
        // must pass validation.
        String refACompact = "syntax = \"proto3\";\n"
                + "package poison;\n"
                + "message Token { string id = 1; int32 version = 2; }\n";
        String refBExpanded = "syntax    =    \"proto3\";\n"
                + "\n"
                + "package   poison;\n"
                + "\n"
                + "\n"
                + "message Token {\n"
                + "\t  string  id       = 1;\n"
                + "\n"
                + "      int32     version = 2;\n"
                + "\n"
                + "}\n";
        String mainSchema = "syntax = \"proto3\";\n"
                + "package poison;\n"
                + "import \"refA.proto\";\n"
                + "import \"refB.proto\";\n"
                + "message Client { string id = 1; }\n";

        TypedContent main = TypedContent.create(ContentHandle.create(mainSchema),
                ContentTypes.APPLICATION_PROTOBUF);
        Map<String, TypedContent> refs = new LinkedHashMap<>();
        refs.put("refA.proto", TypedContent.create(ContentHandle.create(refACompact),
                ContentTypes.APPLICATION_PROTOBUF));
        refs.put("refB.proto", TypedContent.create(ContentHandle.create(refBExpanded),
                ContentTypes.APPLICATION_PROTOBUF));

        ProtobufContentValidator validator = new ProtobufContentValidator();
        validator.validate(ValidityLevel.SYNTAX_ONLY, main, refs);
    }

    @Test
    public void testAllowsIdenticalFqnWithBothWhitespaceAndCommentDifferences() throws Exception {
        // Combined regression: a registry round-trip that strips comments and one that
        // reformats whitespace must both compare equal to the original, because the
        // DescriptorProto form carries neither.
        String original = "syntax = \"proto3\";\n"
                + "package poison;\n"
                + "// Comprehensive token definition.\n"
                + "message Token {\n"
                + "  string id = 1;                 // primary key\n"
                + "  int32 version = 2;             // schema version\n"
                + "  repeated string scopes = 3;    // granted scopes\n"
                + "}\n";
        String commentStrippedReformatted = "syntax=\"proto3\";package poison;"
                + "message Token{string id=1;int32 version=2;repeated string scopes=3;}\n";
        String mainSchema = "syntax = \"proto3\";\n"
                + "package poison;\n"
                + "import \"refA.proto\";\n"
                + "import \"refB.proto\";\n"
                + "message Client { string id = 1; }\n";

        TypedContent main = TypedContent.create(ContentHandle.create(mainSchema),
                ContentTypes.APPLICATION_PROTOBUF);
        Map<String, TypedContent> refs = new LinkedHashMap<>();
        refs.put("refA.proto", TypedContent.create(ContentHandle.create(original),
                ContentTypes.APPLICATION_PROTOBUF));
        refs.put("refB.proto", TypedContent.create(ContentHandle.create(commentStrippedReformatted),
                ContentTypes.APPLICATION_PROTOBUF));

        ProtobufContentValidator validator = new ProtobufContentValidator();
        validator.validate(ValidityLevel.SYNTAX_ONLY, main, refs);
    }

    @Test
    public void testRejectsConflictingEnumAcrossReferences() {
        // Enum redefinitions must be caught just like message redefinitions, since the
        // recursion in ProtobufFqnConflictDetector walks TypeElement uniformly.
        String mainSchema = "syntax = \"proto3\";\n"
                + "package poison;\n"
                + "import \"refA.proto\";\n"
                + "import \"refB.proto\";\n"
                + "message Client { string id = 1; }\n";
        String refASchema = "syntax = \"proto3\";\n"
                + "package poison;\n"
                + "enum Role { UNKNOWN = 0; ADMIN = 1; }\n";
        String refBSchema = "syntax = \"proto3\";\n"
                + "package poison;\n"
                + "enum Role { UNKNOWN = 0; GUEST = 1; }\n";

        TypedContent main = TypedContent.create(ContentHandle.create(mainSchema),
                ContentTypes.APPLICATION_PROTOBUF);
        Map<String, TypedContent> refs = new LinkedHashMap<>();
        refs.put("refA.proto", TypedContent.create(ContentHandle.create(refASchema),
                ContentTypes.APPLICATION_PROTOBUF));
        refs.put("refB.proto", TypedContent.create(ContentHandle.create(refBSchema),
                ContentTypes.APPLICATION_PROTOBUF));

        ProtobufContentValidator validator = new ProtobufContentValidator();
        RuleViolationException ex = Assertions.assertThrows(RuleViolationException.class,
                () -> validator.validate(ValidityLevel.SYNTAX_ONLY, main, refs));
        Assertions.assertTrue(ex.getMessage().contains("poison.Role"),
                "Expected FQN poison.Role in message, got: " + ex.getMessage());
    }

    @Test
    public void testRejectsConflictingNestedTypeAcrossReferences() {
        // Nested types must also be covered. Outer.Inner is the same FQN across both refs
        // but with different fields, which must be rejected.
        String mainSchema = "syntax = \"proto3\";\n"
                + "package poison;\n"
                + "import \"refA.proto\";\n"
                + "import \"refB.proto\";\n"
                + "message Client { string id = 1; }\n";
        String refASchema = "syntax = \"proto3\";\n"
                + "package poison;\n"
                + "message Outer { message Inner { string a = 1; } }\n";
        String refBSchema = "syntax = \"proto3\";\n"
                + "package poison;\n"
                + "message Outer { message Inner { bool admin = 1; } }\n";

        TypedContent main = TypedContent.create(ContentHandle.create(mainSchema),
                ContentTypes.APPLICATION_PROTOBUF);
        Map<String, TypedContent> refs = new LinkedHashMap<>();
        refs.put("refA.proto", TypedContent.create(ContentHandle.create(refASchema),
                ContentTypes.APPLICATION_PROTOBUF));
        refs.put("refB.proto", TypedContent.create(ContentHandle.create(refBSchema),
                ContentTypes.APPLICATION_PROTOBUF));

        ProtobufContentValidator validator = new ProtobufContentValidator();
        RuleViolationException ex = Assertions.assertThrows(RuleViolationException.class,
                () -> validator.validate(ValidityLevel.SYNTAX_ONLY, main, refs));
        Assertions.assertTrue(ex.getMessage().contains("poison.Outer"),
                "Expected FQN containing poison.Outer, got: " + ex.getMessage());
    }

    @Test
    public void testRejectsConflictingServiceAcrossReferences() {
        // Services are also indexed by FQN and must be compared across refs.
        String mainSchema = "syntax = \"proto3\";\n"
                + "package poison;\n"
                + "import \"refA.proto\";\n"
                + "import \"refB.proto\";\n"
                + "message Client { string id = 1; }\n";
        String refASchema = "syntax = \"proto3\";\n"
                + "package poison;\n"
                + "message Req { string id = 1; }\n"
                + "message Resp { string id = 1; }\n"
                + "service Api { rpc One (Req) returns (Resp); }\n";
        String refBSchema = "syntax = \"proto3\";\n"
                + "package poison;\n"
                + "message Req { string id = 1; }\n"
                + "message Resp { string id = 1; }\n"
                + "service Api { rpc Two (Req) returns (Resp); }\n";

        TypedContent main = TypedContent.create(ContentHandle.create(mainSchema),
                ContentTypes.APPLICATION_PROTOBUF);
        Map<String, TypedContent> refs = new LinkedHashMap<>();
        refs.put("refA.proto", TypedContent.create(ContentHandle.create(refASchema),
                ContentTypes.APPLICATION_PROTOBUF));
        refs.put("refB.proto", TypedContent.create(ContentHandle.create(refBSchema),
                ContentTypes.APPLICATION_PROTOBUF));

        ProtobufContentValidator validator = new ProtobufContentValidator();
        RuleViolationException ex = Assertions.assertThrows(RuleViolationException.class,
                () -> validator.validate(ValidityLevel.SYNTAX_ONLY, main, refs));
        Assertions.assertTrue(ex.getMessage().contains("poison.Api"),
                "Expected FQN poison.Api in message, got: " + ex.getMessage());
    }

    @Test
    public void testRejectsUnsafeIdentifierInReference() {
        // The identifier check must fire for malicious references too, not only main.
        DescriptorProtos.DescriptorProto message = DescriptorProtos.DescriptorProto.newBuilder()
                .setName("Data(){console}")
                .build();
        DescriptorProtos.FileDescriptorProto fd = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("refA.proto")
                .setSyntax("proto3")
                .setPackage("poison")
                .addMessageType(message)
                .build();
        String base64Ref = Base64.getEncoder().encodeToString(fd.toByteArray());

        String mainSchema = "syntax = \"proto3\";\n"
                + "package poison;\n"
                + "import \"refA.proto\";\n"
                + "message Client { string id = 1; }\n";

        TypedContent main = TypedContent.create(ContentHandle.create(mainSchema),
                ContentTypes.APPLICATION_PROTOBUF);
        Map<String, TypedContent> refs = new LinkedHashMap<>();
        refs.put("refA.proto", TypedContent.create(ContentHandle.create(base64Ref),
                ContentTypes.APPLICATION_PROTOBUF));

        ProtobufContentValidator validator = new ProtobufContentValidator();
        RuleViolationException ex = Assertions.assertThrows(RuleViolationException.class,
                () -> validator.validate(ValidityLevel.SYNTAX_ONLY, main, refs));
        Assertions.assertTrue(ex.getMessage().contains("refA.proto"),
                "Expected source name refA.proto in message, got: " + ex.getMessage());
    }

    @Test
    public void testRejectsMissingNameInBinaryDescriptor() {
        // A descriptor whose message has an empty name triggers protobuf-java's
        // "Missing name" DescriptorValidationException, which the detector rewrites into
        // a RuleViolationException.
        DescriptorProtos.DescriptorProto message = DescriptorProtos.DescriptorProto.newBuilder()
                .setName("")
                .build();
        DescriptorProtos.FileDescriptorProto fd = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("empty-name.proto")
                .setSyntax("proto3")
                .setPackage("poison")
                .addMessageType(message)
                .build();
        String base64Descriptor = Base64.getEncoder().encodeToString(fd.toByteArray());
        TypedContent content = TypedContent.create(ContentHandle.create(base64Descriptor),
                ContentTypes.APPLICATION_PROTOBUF);

        ProtobufContentValidator validator = new ProtobufContentValidator();
        Assertions.assertThrows(RuleViolationException.class,
                () -> validator.validate(ValidityLevel.SYNTAX_ONLY, content, Collections.emptyMap()));
    }

    @Test
    public void testRejectsUnsafeFieldNameInBinaryDescriptor() {
        // Field-name injection must also be rejected, not just message-name injection.
        DescriptorProtos.FieldDescriptorProto field = DescriptorProtos.FieldDescriptorProto.newBuilder()
                .setName("bad name with spaces")
                .setNumber(1)
                .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING)
                .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL)
                .build();
        DescriptorProtos.DescriptorProto message = DescriptorProtos.DescriptorProto.newBuilder()
                .setName("Token")
                .addField(field)
                .build();
        DescriptorProtos.FileDescriptorProto fd = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("bad-field.proto")
                .setSyntax("proto3")
                .setPackage("poison")
                .addMessageType(message)
                .build();
        String base64Descriptor = Base64.getEncoder().encodeToString(fd.toByteArray());
        TypedContent content = TypedContent.create(ContentHandle.create(base64Descriptor),
                ContentTypes.APPLICATION_PROTOBUF);

        ProtobufContentValidator validator = new ProtobufContentValidator();
        Assertions.assertThrows(RuleViolationException.class,
                () -> validator.validate(ValidityLevel.SYNTAX_ONLY, content, Collections.emptyMap()));
    }

    @Test
    public void testRejectsConflictingFqnAcrossReferencesAtFullLevel() {
        // The detector must engage for ValidityLevel.FULL as well.
        String mainSchema = "syntax = \"proto3\";\n"
                + "package poison;\n"
                + "import \"refA.proto\";\n"
                + "import \"refB.proto\";\n"
                + "message Client { string id = 1; }\n";
        String refASchema = "syntax = \"proto3\";\n"
                + "package poison;\n"
                + "message Token { string id = 1; }\n";
        String refBSchema = "syntax = \"proto3\";\n"
                + "package poison;\n"
                + "message Token { bool admin = 1; }\n";

        TypedContent main = TypedContent.create(ContentHandle.create(mainSchema),
                ContentTypes.APPLICATION_PROTOBUF);
        Map<String, TypedContent> refs = new LinkedHashMap<>();
        refs.put("refA.proto", TypedContent.create(ContentHandle.create(refASchema),
                ContentTypes.APPLICATION_PROTOBUF));
        refs.put("refB.proto", TypedContent.create(ContentHandle.create(refBSchema),
                ContentTypes.APPLICATION_PROTOBUF));

        ProtobufContentValidator validator = new ProtobufContentValidator();
        Assertions.assertThrows(RuleViolationException.class,
                () -> validator.validate(ValidityLevel.FULL, main, refs));
    }

    @Test
    public void testAllowsNullResolvedReferences() throws Exception {
        // Passing null for the references map must not trip the detector's null guards.
        TypedContent content = resourceToTypedContentHandle("protobuf-valid.proto");
        ProtobufContentValidator validator = new ProtobufContentValidator();
        validator.validate(ValidityLevel.SYNTAX_ONLY, content, null);
    }

    @Test
    public void testRejectsConflictMixedBinaryAndTextReferences() {
        // One reference is text, the other is a base64 FileDescriptorProto defining the
        // same FQN with a different field. Both code paths must cooperate and the conflict
        // must still be rejected.
        DescriptorProtos.FieldDescriptorProto adminField = DescriptorProtos.FieldDescriptorProto.newBuilder()
                .setName("admin")
                .setNumber(1)
                .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_BOOL)
                .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL)
                .build();
        DescriptorProtos.DescriptorProto binaryToken = DescriptorProtos.DescriptorProto.newBuilder()
                .setName("Token")
                .addField(adminField)
                .build();
        DescriptorProtos.FileDescriptorProto fd = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("refB.proto")
                .setSyntax("proto3")
                .setPackage("poison")
                .addMessageType(binaryToken)
                .build();
        String refBBinary = Base64.getEncoder().encodeToString(fd.toByteArray());

        String refAText = "syntax = \"proto3\";\n"
                + "package poison;\n"
                + "message Token { string id = 1; }\n";
        String mainSchema = "syntax = \"proto3\";\n"
                + "package poison;\n"
                + "import \"refA.proto\";\n"
                + "import \"refB.proto\";\n"
                + "message Client { string id = 1; }\n";

        TypedContent main = TypedContent.create(ContentHandle.create(mainSchema),
                ContentTypes.APPLICATION_PROTOBUF);
        Map<String, TypedContent> refs = new LinkedHashMap<>();
        refs.put("refA.proto", TypedContent.create(ContentHandle.create(refAText),
                ContentTypes.APPLICATION_PROTOBUF));
        refs.put("refB.proto", TypedContent.create(ContentHandle.create(refBBinary),
                ContentTypes.APPLICATION_PROTOBUF));

        ProtobufContentValidator validator = new ProtobufContentValidator();
        RuleViolationException ex = Assertions.assertThrows(RuleViolationException.class,
                () -> validator.validate(ValidityLevel.SYNTAX_ONLY, main, refs));
        Assertions.assertTrue(ex.getMessage().contains("poison.Token"),
                "Expected FQN poison.Token in message, got: " + ex.getMessage());
    }

    @Test
    public void testAllowsProto2Extensions() throws Exception {
        // Validation frameworks like protoc-gen-validate extend google.protobuf.FieldOptions
        // to add custom field annotations. Validation must not reject schemas that use this
        // pattern: the binary check allows unknown deps, and the cross-file walk indexes
        // messages/enums/services but not extend declarations.
        String schema = "syntax = \"proto2\";\n"
                + "package sample;\n"
                + "import \"google/protobuf/descriptor.proto\";\n"
                + "extend google.protobuf.FieldOptions {\n"
                + "  optional string validate_rule = 50000;\n"
                + "}\n"
                + "message User {\n"
                + "  optional string email = 1 [(validate_rule) = \"email\"];\n"
                + "}\n";

        TypedContent content = TypedContent.create(ContentHandle.create(schema),
                ContentTypes.APPLICATION_PROTOBUF);
        ProtobufContentValidator validator = new ProtobufContentValidator();
        validator.validate(ValidityLevel.SYNTAX_ONLY, content, Collections.emptyMap());
    }

    @Test
    public void testAllowsExtensionUsageWithReference() throws Exception {
        // Real-world validation-framework pattern: a shared file defines a custom field
        // option, and the main artifact imports it to annotate its own messages. The
        // extension definition file imports google/protobuf/descriptor.proto, which is
        // not in FileDescriptorUtils.WELL_KNOWN_DEPENDENCIES, so we supply it as a
        // base64-encoded reference — this mirrors how a registry client would register
        // descriptor.proto as a shared artifact.
        String descriptorProtoBase64 = Base64.getEncoder().encodeToString(
                DescriptorProtos.FileDescriptorProto.getDescriptor().getFile().toProto().toByteArray());

        String validateProto = "syntax = \"proto2\";\n"
                + "package sample;\n"
                + "import \"google/protobuf/descriptor.proto\";\n"
                + "extend google.protobuf.FieldOptions {\n"
                + "  optional string validate_rule = 50000;\n"
                + "}\n";
        String mainSchema = "syntax = \"proto2\";\n"
                + "package sample;\n"
                + "import \"validate.proto\";\n"
                + "message User {\n"
                + "  optional string email = 1 [(validate_rule) = \"email\"];\n"
                + "}\n";

        TypedContent main = TypedContent.create(ContentHandle.create(mainSchema),
                ContentTypes.APPLICATION_PROTOBUF);
        Map<String, TypedContent> refs = new LinkedHashMap<>();
        refs.put("google/protobuf/descriptor.proto",
                TypedContent.create(ContentHandle.create(descriptorProtoBase64),
                        ContentTypes.APPLICATION_PROTOBUF));
        refs.put("validate.proto", TypedContent.create(ContentHandle.create(validateProto),
                ContentTypes.APPLICATION_PROTOBUF));

        ProtobufContentValidator validator = new ProtobufContentValidator();
        validator.validate(ValidityLevel.SYNTAX_ONLY, main, refs);
    }

    @Test
    public void testValidateReferences() throws Exception {
        TypedContent content = resourceToTypedContentHandle("protobuf-valid-with-refs.proto");
        ProtobufContentValidator validator = new ProtobufContentValidator();

        // Properly map both required references - success.
        {
            List<ArtifactReference> references = new ArrayList<>();
            references.add(ArtifactReference.builder().groupId("default").artifactId("message2.proto")
                    .version("1.0").name("message2.proto").build());
            references.add(ArtifactReference.builder().groupId("default").artifactId("message3.proto")
                    .version("1.1").name("message3.proto").build());
            validator.validateReferences(content, references);
        }

        // Don't map either of the required references - failure.
        Assertions.assertThrows(RuleViolationException.class, () -> {
            List<ArtifactReference> references = new ArrayList<>();
            validator.validateReferences(content, references);
        });

        // Only map one of the two required refs - failure.
        Assertions.assertThrows(RuleViolationException.class, () -> {
            List<ArtifactReference> references = new ArrayList<>();
            references.add(ArtifactReference.builder().groupId("default").artifactId("message2.proto")
                    .version("1.0").name("message2.proto").build());
            validator.validateReferences(content, references);
        });

        // Only map one of the two required refs - failure.
        Assertions.assertThrows(RuleViolationException.class, () -> {
            List<ArtifactReference> references = new ArrayList<>();
            references.add(ArtifactReference.builder().groupId("default").artifactId("message2.proto")
                    .version("1.0").name("message2.proto").build());
            references.add(ArtifactReference.builder().groupId("default").artifactId("message4.proto")
                    .version("4.0").name("message4.proto").build());
            validator.validateReferences(content, references);
        });
    }

}
