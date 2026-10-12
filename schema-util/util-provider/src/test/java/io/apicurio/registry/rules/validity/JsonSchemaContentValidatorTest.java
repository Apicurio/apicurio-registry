package io.apicurio.registry.rules.validity;

import com.sun.net.httpserver.HttpServer;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.json.rules.validity.JsonSchemaContentValidator;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.types.ContentTypes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Tests the JSON Schema content validator.
 */
public class JsonSchemaContentValidatorTest extends ArtifactUtilProviderTestBase {

    @Test
    public void testValidJsonSchema() throws Exception {
        TypedContent content = resourceToTypedContentHandle("jsonschema-valid.json");
        JsonSchemaContentValidator validator = new JsonSchemaContentValidator();
        validator.validate(ValidityLevel.SYNTAX_ONLY, content, Collections.emptyMap());
    }

    @Test
    public void testInvalidJsonSchema() throws Exception {
        TypedContent content = resourceToTypedContentHandle("jsonschema-invalid.json");
        JsonSchemaContentValidator validator = new JsonSchemaContentValidator();
        Assertions.assertThrows(RuleViolationException.class, () -> {
            validator.validate(ValidityLevel.SYNTAX_ONLY, content, Collections.emptyMap());
        });
    }

    @Test
    public void testInvalidJsonSchemaVersion() throws Exception {
        TypedContent content = resourceToTypedContentHandle("jsonschema-valid-d7.json");
        JsonSchemaContentValidator validator = new JsonSchemaContentValidator();
        validator.validate(ValidityLevel.FULL, content, Collections.emptyMap());
    }

    @Test
    public void testInvalidJsonSchemaFull() throws Exception {
        TypedContent content = resourceToTypedContentHandle("bad-json-schema-v1.json");
        JsonSchemaContentValidator validator = new JsonSchemaContentValidator();
        RuleViolationException error = Assertions.assertThrows(RuleViolationException.class, () -> {
            validator.validate(ValidityLevel.FULL, content, Collections.emptyMap());
        });
        // Draft 7 requires a number. The violation points at the keyword, as a JSON Pointer.
        Assertions.assertTrue(error.getCauses().stream().anyMatch(cause ->
                        "/items/properties/price/exclusiveMinimum".equals(cause.getContext())
                                && "boolean found, number expected".equals(cause.getDescription())),
                () -> "No violation at the keyword: " + error.getCauses());
    }

    @Test
    public void testJsonSchemaWithReferences() throws Exception {
        TypedContent city = resourceToTypedContentHandle("city.json");
        TypedContent citizen = resourceToTypedContentHandle("citizen.json");
        JsonSchemaContentValidator validator = new JsonSchemaContentValidator();
        validator.validate(ValidityLevel.FULL, citizen,
                Collections.singletonMap("https://example.com/city.json", city));
    }

    /**
     * A document Registry has no content for is never fetched: it is replaced by one that accepts
     * anything, so the schema is valid whatever the reference points to, including when the
     * reference has a fragment, a JSON Pointer or an anchor. That holds for a reference inside
     * supplied content too. A local
     * server records whether any request was made.
     */
    @ParameterizedTest
    @ValueSource(strings = {"http://json-schema.org/draft-04/schema#", "http://json-schema.org/draft-06/schema#",
            "http://json-schema.org/draft-07/schema#", "https://json-schema.org/draft/2019-09/schema",
            "https://json-schema.org/draft/2020-12/schema"})
    public void testReferenceWithoutContentIsNotFetched(String draft) throws Exception {
        List<String> requests = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.add(exchange.getRequestURI().toString());
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            TypedContent content = TypedContent.create(ContentHandle.create("""
                    {
                      "$schema": "%s",
                      "properties": {
                        "a": { "$ref": "%s/missing.json" },
                        "b": { "$ref": "%s/missing.json#/definitions/b" },
                        "c": { "$ref": "https://example.com/supplied.json" },
                        "e": { "$ref": "%s/missing.json#e" }
                      }
                    }
                    """.formatted(draft, base, base, base)), ContentTypes.APPLICATION_JSON);
            TypedContent supplied = TypedContent.create(ContentHandle.create("""
                    { "properties": { "d": { "$ref": "%s/nested.json" } } }
                    """.formatted(base)), ContentTypes.APPLICATION_JSON);

            new JsonSchemaContentValidator().validate(ValidityLevel.FULL, content,
                    Map.of("https://example.com/supplied.json", supplied));
            Assertions.assertEquals(List.of(), requests, "No document may be fetched");
        } finally {
            server.stop(0);
        }
    }

    /**
     * Loading the schema catches what its meta-schema can't, such as a reference to a definition the
     * schema doesn't have.
     */
    @ParameterizedTest
    @ValueSource(strings = {"http://json-schema.org/draft-04/schema#", "http://json-schema.org/draft-07/schema#",
            "https://json-schema.org/draft/2019-09/schema", "https://json-schema.org/draft/2020-12/schema"})
    public void testMissingDefinitionIsRejected(String draft) throws Exception {
        TypedContent content = TypedContent.create(ContentHandle.create("""
                {
                  "$schema": "%s",
                  "definitions": { "a": { "type": "string" } },
                  "properties": { "x": { "$ref": "#/definitions/missing" } }
                }
                """.formatted(draft)), ContentTypes.APPLICATION_JSON);
        Assertions.assertThrows(RuleViolationException.class,
                () -> new JsonSchemaContentValidator().validate(ValidityLevel.FULL, content, Collections.emptyMap()));
    }

    /**
     * A {@code $schema} that names no known draft, such as a custom meta-schema, is validated as
     * draft 7, rather than rejected.
     */
    @Test
    public void testUnrecognisedDraftIsReadAsDraft7() throws Exception {
        JsonSchemaContentValidator validator = new JsonSchemaContentValidator();
        String schema = """
                { "$schema": "https://example.com/my-meta-schema", "type": "number", "exclusiveMinimum": %s }
                """;
        validator.validate(ValidityLevel.FULL,
                TypedContent.create(ContentHandle.create(schema.formatted("0")), ContentTypes.APPLICATION_JSON),
                Collections.emptyMap());

        // Draft 7 requires a number here, where draft 4 required a boolean.
        RuleViolationException error = Assertions.assertThrows(RuleViolationException.class,
                () -> validator.validate(ValidityLevel.FULL,
                        TypedContent.create(ContentHandle.create(schema.formatted("true")), ContentTypes.APPLICATION_JSON),
                        Collections.emptyMap()));
        Assertions.assertEquals("/exclusiveMinimum", error.getCauses().iterator().next().getContext());
    }

    /**
     * A reference recorded for the artifact that its content does not use is most likely a typo in
     * the reference name, so it is rejected rather than ignored.
     */
    @Test
    public void testUnusedReferenceIsRejected() throws Exception {
        TypedContent content = resourceToTypedContentHandle("jsonschema-valid-d7.json");
        TypedContent city = resourceToTypedContentHandle("city.json");
        RuleViolationException error = Assertions.assertThrows(RuleViolationException.class,
                () -> new JsonSchemaContentValidator().validate(ValidityLevel.FULL, content,
                        Map.of("https://example.com/city.json", city)));
        String description = error.getCauses().iterator().next().getDescription();
        Assertions.assertTrue(description.contains("Unused reference records: https://example.com/city.json"),
                () -> "The violation should name the unused reference: " + description);
    }

    @Test
    public void testValidateReferences() throws Exception {
        TypedContent content = resourceToTypedContentHandle("jsonschema-valid-with-refs.json");
        JsonSchemaContentValidator validator = new JsonSchemaContentValidator();

        // Properly map both required references - success.
        {
            List<ArtifactReference> references = new ArrayList<>();
            references.add(ArtifactReference.builder()
                    .groupId("default")
                    .artifactId("Category")
                    .version("1.0")
                    .name("example.com/schemas/Category")
                    .build());
            references.add(ArtifactReference.builder()
                    .groupId("default")
                    .artifactId("Customer")
                    .version("1.1")
                    .name("example.com/schemas/Customer")
                    .build());
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
            references.add(ArtifactReference.builder()
                    .groupId("default")
                    .artifactId("Category")
                    .version("1.0")
                    .name("example.com/schemas/Category")
                    .build());
            validator.validateReferences(content, references);
        });

        // Only map one of the two required refs - failure.
        Assertions.assertThrows(RuleViolationException.class, () -> {
            List<ArtifactReference> references = new ArrayList<>();
            references.add(ArtifactReference.builder()
                    .groupId("default")
                    .artifactId("Category")
                    .version("1.0")
                    .name("example.com/schemas/Category")
                    .build());
            references.add(ArtifactReference.builder()
                    .groupId("default")
                    .artifactId("WrongSchema")
                    .version("2.3")
                    .name("example.com/schemas/WrongSchema")
                    .build());
            validator.validateReferences(content, references);
        });
    }

    @Test
    public void testValidateReferencesWithNullReferenceList() throws Exception {
        TypedContent contentWithoutRefs = resourceToTypedContentHandle("jsonschema-valid.json");
        TypedContent contentWithRefs = resourceToTypedContentHandle("jsonschema-valid-with-refs.json");
        JsonSchemaContentValidator validator = new JsonSchemaContentValidator();

        validator.validateReferences(contentWithoutRefs, null);

        Assertions.assertThrows(RuleViolationException.class, () -> {
            validator.validateReferences(contentWithRefs, null);
        });
    }
}
