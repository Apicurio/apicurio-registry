package io.apicurio.registry.content.util;

import com.fasterxml.jackson.databind.JsonNode;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ContentTypeUtilTest {

    @Test
    public void testIsParsableXml_validXml() {
        ContentHandle content = ContentHandle.create("<root><child>text</child></root>");
        Assertions.assertTrue(ContentTypeUtil.isParsableXml(content));
    }

    @Test
    public void testIsParsableXml_malformedXml() {
        ContentHandle content = ContentHandle.create("<root><unclosed>");
        Assertions.assertFalse(ContentTypeUtil.isParsableXml(content));
    }

    @Test
    public void testIsParsableXml_nonXml() {
        ContentHandle content = ContentHandle.create("this is not xml at all");
        Assertions.assertFalse(ContentTypeUtil.isParsableXml(content));
    }

    @Test
    public void testIsParsableXml_rejectsDoctypeDeclaration() {
        String xxePayload = "<?xml version=\"1.0\"?>\n"
                + "<!DOCTYPE foo [\n"
                + "  <!ENTITY xxe SYSTEM \"file:///etc/passwd\">\n"
                + "]>\n"
                + "<root>&xxe;</root>";
        ContentHandle content = ContentHandle.create(xxePayload);
        Assertions.assertFalse(ContentTypeUtil.isParsableXml(content));
    }

    @Test
    public void testIsParsableXml_rejectsEntityExpansion() {
        String billionLaughs = "<?xml version=\"1.0\"?>\n"
                + "<!DOCTYPE lolz [\n"
                + "  <!ENTITY lol \"lol\">\n"
                + "]>\n"
                + "<root>&lol;</root>";
        ContentHandle content = ContentHandle.create(billionLaughs);
        Assertions.assertFalse(ContentTypeUtil.isParsableXml(content));
    }

    @Test
    public void testParseJsonOrYaml_nullContentType_parsesAsJson() throws Exception {
        // Callers (content accepters, reference finders, validators) let a null content type
        // through on purpose. parseJsonOrYaml must treat null as unknown and fall back to JSON
        // instead of throwing an NPE on contentType.toLowerCase().
        TypedContent content = TypedContent.create(ContentHandle.create("{\"name\": \"widget\"}"), null);

        JsonNode node = ContentTypeUtil.parseJsonOrYaml(content);

        Assertions.assertTrue(node.isObject());
        Assertions.assertEquals("widget", node.get("name").asText());
    }

    @Test
    public void testParseJsonOrYaml_nullContentType_jsonObject() throws Exception {
        TypedContent content = TypedContent.create(ContentHandle.create("{\"a\": 1, \"b\": 2}"), null);

        JsonNode node = ContentTypeUtil.parseJsonOrYaml(content);

        Assertions.assertTrue(node.isObject());
        Assertions.assertEquals(1, node.get("a").asInt());
        Assertions.assertEquals(2, node.get("b").asInt());
    }

    @Test
    public void testParseJsonOrYaml_nullContentType_yamlDocument() throws Exception {
        // A YAML document submitted without a content type must still be detected: the null
        // branch tries JSON first, then falls back to YAML on a parse failure. Without the
        // fallback this document would fail to parse as JSON and be silently rejected.
        TypedContent content = TypedContent.create(ContentHandle.create("name: widget\ncount: 3\n"), null);

        JsonNode node = ContentTypeUtil.parseJsonOrYaml(content);

        Assertions.assertTrue(node.isObject());
        Assertions.assertEquals("widget", node.get("name").asText());
        Assertions.assertEquals(3, node.get("count").asInt());
    }

    @Test
    public void testParseJsonOrYaml_yamlContentType() throws Exception {
        // An explicit YAML content type must still route through the YAML branch.
        TypedContent content = TypedContent.create(ContentHandle.create("name: widget\ncount: 3\n"),
                "application/x-yaml");

        JsonNode node = ContentTypeUtil.parseJsonOrYaml(content);

        Assertions.assertTrue(node.isObject());
        Assertions.assertEquals("widget", node.get("name").asText());
        Assertions.assertEquals(3, node.get("count").asInt());
    }
}
