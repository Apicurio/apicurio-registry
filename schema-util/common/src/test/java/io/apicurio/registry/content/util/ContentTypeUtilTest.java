package io.apicurio.registry.content.util;

import com.fasterxml.jackson.databind.JsonNode;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.types.ContentTypes;
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

    @Test
    public void testIsParsableGraphQL() {
        // Valid GraphQL schema
        ContentHandle content = ContentHandle.create("type Query { hello: String }");
        Assertions.assertTrue(ContentTypeUtil.isParsableGraphQL(content));

        // GraphQL schema keyword
        ContentHandle content2 = ContentHandle.create("schema{ query: Query }");
        Assertions.assertTrue(ContentTypeUtil.isParsableGraphQL(content2));

        // GraphQL directive
        ContentHandle contentDirective = ContentHandle.create("directive @deprecated(reason: String = \"No longer supported\") on FIELD_DEFINITION | ENUM_VALUE");
        Assertions.assertTrue(ContentTypeUtil.isParsableGraphQL(contentDirective));

        // GraphQL extend keyword
        ContentHandle contentExtend = ContentHandle.create("extend type Foo { bar: String }");
        Assertions.assertTrue(ContentTypeUtil.isParsableGraphQL(contentExtend));

        // GraphQL with tab whitespace
        ContentHandle content3 = ContentHandle.create("type\tQuery { hello: String }");
        Assertions.assertTrue(ContentTypeUtil.isParsableGraphQL(content3));

        // Protobuf should not be detected as GraphQL
        ContentHandle notGraphql = ContentHandle.create("message Hello { string msg = 1; }");
        Assertions.assertFalse(ContentTypeUtil.isParsableGraphQL(notGraphql));

        // Protobuf enum should not be detected as GraphQL
        ContentHandle protobufEnum = ContentHandle.create("enum Status { ACTIVE = 0; }");
        Assertions.assertFalse(ContentTypeUtil.isParsableGraphQL(protobufEnum));

        // GraphQL schema with newline should be detected
        ContentHandle schemaNewline = ContentHandle.create("schema\n{\n  query: Query\n}");
        Assertions.assertTrue(ContentTypeUtil.isParsableGraphQL(schemaNewline));

        // JSON-like content should not be detected as GraphQL
        ContentHandle jsonLike = ContentHandle.create("{ \"key\": \"value\" }");
        Assertions.assertFalse(ContentTypeUtil.isParsableGraphQL(jsonLike));

        // XML-like content should not be detected as GraphQL
        ContentHandle xmlLike = ContentHandle.create("<root><child/></root>");
        Assertions.assertFalse(ContentTypeUtil.isParsableGraphQL(xmlLike));

        // Empty content should not be detected as GraphQL
        ContentHandle empty = ContentHandle.create("");
        Assertions.assertFalse(ContentTypeUtil.isParsableGraphQL(empty));
    }

    @Test
    public void testDetermineContentType_GraphQL() {
        // GraphQL schema that was previously misclassified as YAML
        ContentHandle content = ContentHandle.create("type Query { hello: String }");
        Assertions.assertEquals(ContentTypes.APPLICATION_GRAPHQL, ContentTypeUtil.determineContentType(content));
    }

    @Test
    public void testDetermineContentType_YamlStillWorks() {
        // Normal YAML document should still resolve to application/x-yaml
        ContentHandle yaml = ContentHandle.create("foo: bar\nbaz: qux\n");
        Assertions.assertEquals("application/x-yaml", ContentTypeUtil.determineContentType(yaml));

        // Nested YAML
        ContentHandle nestedYaml = ContentHandle.create("apiVersion: v1\nkind: Service\nmetadata:\n  name: test\n");
        Assertions.assertEquals("application/x-yaml", ContentTypeUtil.determineContentType(nestedYaml));
    }

    @Test
    public void testDetermineContentType_EdgeCases() {
        // JSON should still be detected as JSON, not GraphQL
        ContentHandle json = ContentHandle.create("{\"type\": \"Query\"}");
        Assertions.assertEquals("application/json", ContentTypeUtil.determineContentType(json));

        // XML should still be detected as XML
        ContentHandle xml = ContentHandle.create("<type>Query</type>");
        Assertions.assertEquals("application/xml", ContentTypeUtil.determineContentType(xml));

        // Protobuf should still be the fallback
        ContentHandle protobuf = ContentHandle.create("message Foo { string bar = 1; }");
        Assertions.assertEquals(ContentTypes.APPLICATION_PROTOBUF, ContentTypeUtil.determineContentType(protobuf));
    }

    @Test
    public void testIsParsableGraphQL_rejectsYamlWithKeywords() {
        String yamlContent = "type: Query\nfields:\n  - name: hello\n";
        ContentHandle yaml = ContentHandle.create(yamlContent);
        
        // Should not be detected as GraphQL despite having "type: Query"
        Assertions.assertFalse(ContentTypeUtil.isParsableGraphQL(yaml));
        
        // Should correctly fall back to YAML
        Assertions.assertEquals(ContentTypes.APPLICATION_YAML, ContentTypeUtil.determineContentType(yaml));
    }
}
