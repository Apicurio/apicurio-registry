package io.apicurio.registry.json.content.refs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.json.content.refs.JsonSchemaRefScopes.RefSite;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

public class JsonSchemaRefScopesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Returns each found $ref mapped to its resolved URI (or null), in document order.
     */
    private static Map<String, String> sites(String schema) throws Exception {
        JsonNode tree = MAPPER.readTree(schema);
        Map<String, String> result = new LinkedHashMap<>();
        for (RefSite site : JsonSchemaRefScopes.find(tree)) {
            result.put(site.ref(), site.resolved());
        }
        return result;
    }

    @Test
    public void testRootIdResolvesRelativeRef() throws Exception {
        Map<String, String> sites = sites("""
                {"$id": "https://example.com/schemas/customer/",
                 "properties": {"address": {"$ref": "address.json#/definitions/Address"}}}
                """);
        Assertions.assertEquals(
                Map.of("address.json#/definitions/Address",
                        "https://example.com/schemas/customer/address.json#/definitions/Address"),
                sites);
    }

    @Test
    public void testNestedIdChangesScope() throws Exception {
        Map<String, String> sites = sites("""
                {"$schema": "https://json-schema.org/draft/2020-12/schema",
                 "$id": "https://example.com/schemas/",
                 "properties": {
                   "top": {"$ref": "top.json"},
                   "customer": {"$id": "customer/", "properties": {"a": {"$ref": "address.json"}}}
                 }}
                """);
        Assertions.assertEquals("https://example.com/schemas/top.json", sites.get("top.json"));
        Assertions.assertEquals("https://example.com/schemas/customer/address.json", sites.get("address.json"));
    }

    @Test
    public void testDraft4UsesIdKeyword() throws Exception {
        Map<String, String> sites = sites("""
                {"$schema": "http://json-schema.org/draft-04/schema#",
                 "id": "https://example.com/schemas/customer/",
                 "properties": {"a": {"$ref": "address.json"}, "b": {"$id": "ignored/", "$ref": "x.json"}}}
                """);
        Assertions.assertEquals("https://example.com/schemas/customer/address.json", sites.get("address.json"));
        Assertions.assertEquals("https://example.com/schemas/customer/x.json", sites.get("x.json"));
    }

    @Test
    public void testIdNextToRefIgnoredUpToDraft7() throws Exception {
        Map<String, String> sites = sites("""
                {"$schema": "http://json-schema.org/draft-07/schema#",
                 "$id": "https://example.com/root/",
                 "properties": {"a": {"$id": "https://other.example.com/", "$ref": "address.json"}}}
                """);
        Assertions.assertEquals("https://example.com/root/address.json", sites.get("address.json"));
    }

    @Test
    public void testIdNextToRefAppliesFrom2019() throws Exception {
        Map<String, String> sites = sites("""
                {"$schema": "https://json-schema.org/draft/2019-09/schema",
                 "$id": "https://example.com/root/",
                 "properties": {"a": {"$id": "https://other.example.com/", "$ref": "address.json"}}}
                """);
        Assertions.assertEquals("https://other.example.com/address.json", sites.get("address.json"));
    }

    @Test
    public void testFragmentOnlyIdIsAnAnchor() throws Exception {
        Map<String, String> sites = sites("""
                {"$schema": "http://json-schema.org/draft-07/schema#",
                 "$id": "https://example.com/root/",
                 "definitions": {"a": {"$id": "#anchor", "properties": {"x": {"$ref": "address.json"}}}}}
                """);
        Assertions.assertEquals("https://example.com/root/address.json", sites.get("address.json"));
    }

    @Test
    public void testIdAsPropertyNameIsNotAKeyword() throws Exception {
        Map<String, String> sites = sites("""
                {"$id": "https://example.com/root/",
                 "properties": {"$id": {"type": "string"}, "a": {"$ref": "address.json"}}}
                """);
        Assertions.assertEquals("https://example.com/root/address.json", sites.get("address.json"));
    }

    @Test
    public void testNoIdLeavesRefUnresolved() throws Exception {
        Map<String, String> sites = sites("""
                {"properties": {"a": {"$ref": "address.json"}}}
                """);
        Assertions.assertTrue(sites.containsKey("address.json"));
        Assertions.assertNull(sites.get("address.json"));
    }

    @Test
    public void testInvalidUrisDoNotFail() throws Exception {
        Map<String, String> sites = sites("""
                {"$id": "https://example.com/ bad id/",
                 "properties": {"a": {"$ref": "my file.json"}, "b": {"$ref": "address.json"}}}
                """);
        Assertions.assertEquals(2, sites.size());
        Assertions.assertNull(sites.get("my file.json"));
        // The invalid $id is ignored, so there is no base URI
        Assertions.assertNull(sites.get("address.json"));
    }
    @Test
    public void testInternalRefsAreNeverResolved() throws Exception {
        Map<String, String> sites = sites("""
                {"$id": "Types.json",
                 "properties": {"a": {"$ref": "#/$defs/A"}, "b": {"$ref": "Other.json#/$defs/B"}},
                 "$defs": {"A": {"type": "string"}}}
                """);
        Assertions.assertTrue(sites.containsKey("#/$defs/A"));
        Assertions.assertNull(sites.get("#/$defs/A"));
        // A relative $id resolves a relative $ref to the same value, so it is reported as unchanged
        Assertions.assertNull(sites.get("Other.json#/$defs/B"));
    }
}
