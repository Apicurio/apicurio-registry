package io.apicurio.registry.examples.rules;

import io.apicurio.registry.resolver.config.SchemaResolverConfig;
import io.apicurio.registry.resolver.strategy.ArtifactReference;
import io.apicurio.schema.validation.ValidationResult;
import io.apicurio.schema.validation.avro.AvroValidator;
import io.apicurio.schema.validation.json.JsonValidator;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

/**
 * Validates data, not schemas, on the client: the client-side validation library fetches a schema
 * from the registry, caches it, and checks payloads against it before they are sent anywhere.
 *
 * <p>Registers its own schemas in the group {@code validation}, so it runs against any registry:
 * {@code mvn exec:java -Dregistry.url=http://localhost:8081/apis/registry/v3}. Prints one ok/FAIL
 * line per check and exits 1 if any check fails.
 */
public class ValidationClient {

    private static final String GROUP = "validation";

    private static final String ORDER_JSON_SCHEMA = """
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "properties": {
                "orderId": {"type": "string"},
                "quantity": {"type": "integer", "minimum": 1}
              },
              "required": ["orderId", "quantity"]
            }""";

    private static final String ORDER_AVRO_SCHEMA = """
            {"type": "record", "name": "OrderPlaced", "namespace": "com.example.orders",
             "fields": [{"name": "orderId", "type": "string"},
                        {"name": "amount", "type": "double", "default": 0.0}]}""";

    private static int failures;

    public static void main(String[] args) throws Exception {
        String registryUrl = args.length > 0 ? args[0] : "http://localhost:8081/apis/registry/v3";
        register(registryUrl, "order-json", "JSON", ORDER_JSON_SCHEMA);
        register(registryUrl, "order-avro", "AVRO", ORDER_AVRO_SCHEMA);

        System.out.println("JSON Schema (validation/order-json):");
        JsonValidator json = new JsonValidator(config(registryUrl), Optional.of(reference("order-json")));
        expect("a valid order", true, json.validateByArtifactReference(
                Map.of("orderId", "o-1", "quantity", 2)));
        expect("a quantity of 0 (minimum is 1)", false, json.validateByArtifactReference(
                Map.of("orderId", "o-2", "quantity", 0)));
        expect("no quantity (required)", false, json.validateByArtifactReference(
                Map.of("orderId", "o-3")));

        System.out.println("Avro (validation/order-avro):");
        AvroValidator avro = new AvroValidator(config(registryUrl), Optional.of(reference("order-avro")));
        expect("a valid order", true, avro.validateByArtifactReference(
                "{\"orderId\": \"o-1\", \"amount\": 9.5}"));
        expect("an amount that is not a number", false, avro.validateByArtifactReference(
                "{\"orderId\": \"o-2\", \"amount\": \"lots\"}"));

        if (failures > 0) {
            System.out.println(failures + " check(s) failed.");
            System.exit(1);
        }
        System.out.println("All validation checks passed.");
    }

    private static Map<String, Object> config(String registryUrl) {
        return Map.of(SchemaResolverConfig.REGISTRY_URL, registryUrl);
    }

    private static ArtifactReference reference(String artifactId) {
        return ArtifactReference.builder().groupId(GROUP).artifactId(artifactId).build();
    }

    private static void expect(String description, boolean valid, ValidationResult result) {
        if (result.success() == valid) {
            System.out.printf("  ok    %-45s %s%n", description, valid ? "valid" : "invalid: " + firstError(result));
        } else {
            System.out.printf("  FAIL  %-45s expected %s, got %s%n", description,
                    valid ? "valid" : "invalid", result.success() ? "valid" : firstError(result));
            failures++;
        }
    }

    private static String firstError(ValidationResult result) {
        return result.getValidationErrors().isEmpty() ? "" : result.getValidationErrors().get(0).getDescription();
    }

    /** Creates the artifact, or finds it when it already exists with the same content. */
    private static void register(String registryUrl, String artifactId, String type, String schema)
            throws IOException, InterruptedException {
        String body = "{\"artifactId\": " + quote(artifactId) + ", \"artifactType\": " + quote(type)
                + ", \"firstVersion\": {\"content\": {\"content\": " + quote(schema)
                + ", \"contentType\": \"application/json\"}}}";
        HttpRequest request = HttpRequest.newBuilder(URI.create(registryUrl + "/groups/" + GROUP
                        + "/artifacts?ifExists=FIND_OR_CREATE_VERSION"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Registering " + artifactId + " failed: " + response.statusCode()
                    + " " + response.body());
        }
    }

    private static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                default -> out.append(c);
            }
        }
        return out.append('"').toString();
    }

    private ValidationClient() {
    }
}
