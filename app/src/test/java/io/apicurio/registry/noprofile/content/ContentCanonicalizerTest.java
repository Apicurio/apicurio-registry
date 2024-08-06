package io.apicurio.registry.noprofile.content;

import io.apicurio.registry.AbstractRegistryTestBase;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.canon.ContentCanonicalizer;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProvider;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;

@QuarkusTest
public class ContentCanonicalizerTest extends AbstractRegistryTestBase {

    @Inject
    ArtifactTypeUtilProviderFactory factory;

    private static TypedContent toTypedContent(String content) {
        return toTypedContent(content, ContentTypes.APPLICATION_JSON);
    }

    private static TypedContent toTypedContent(String content, String ct) {
        return TypedContent.create(ContentHandle.create(content), ct);
    }

    private ContentCanonicalizer getContentCanonicalizer(String type) {
        ArtifactTypeUtilProvider provider = factory.getArtifactTypeProvider(type);
        return provider.getContentCanonicalizer();
    }

    @Test
    void testOpenAPI() {
        ContentCanonicalizer canonicalizer = getContentCanonicalizer(ArtifactType.OPENAPI);

        String before = "{\r\n" + "    \"openapi\": \"3.0.2\",\r\n" + "    \"info\": {\r\n"
                + "        \"title\": \"Empty 3.0 API\",\r\n" + "        \"version\": \"1.0.0\"\r\n"
                + "    },\r\n" + "    \"paths\": {\r\n" + "        \"/\": {}\r\n" + "    },\r\n"
                + "    \"components\": {}\r\n" + "}";
        String expected = "{\"components\":{},\"info\":{\"title\":\"Empty 3.0 API\",\"version\":\"1.0.0\"},\"openapi\":\"3.0.2\",\"paths\":{\"/\":{}}}";

        TypedContent content = toTypedContent(before);
        String actual = canonicalizer.canonicalize(content, Collections.emptyMap()).getContent().content();
        Assertions.assertEquals(expected, actual);
    }

    @Test
    void testAvro() {
        ContentCanonicalizer canonicalizer = getContentCanonicalizer(ArtifactType.AVRO);

        String before = "{\r\n" + "     \"type\": \"record\",\r\n"
                + "     \"namespace\": \"com.example\",\r\n" + "     \"name\": \"FullName\",\r\n"
                + "     \"fields\": [\r\n" + "       { \"name\": \"first\", \"type\": \"string\" },\r\n"
                + "       { \"name\": \"middle\", \"type\": \"string\" },\r\n"
                + "       { \"name\": \"last\", \"type\": \"string\" }\r\n" + "     ]\r\n" + "} ";
        String expected = "{\"type\":\"record\",\"name\":\"FullName\",\"namespace\":\"com.example\",\"doc\":\"\",\"fields\":[{\"name\":\"first\",\"type\":\"string\",\"doc\":\"\"},{\"name\":\"middle\",\"type\":\"string\",\"doc\":\"\"},{\"name\":\"last\",\"type\":\"string\",\"doc\":\"\"}]}";

        TypedContent content = toTypedContent(before);
        String actual = canonicalizer.canonicalize(content, Collections.emptyMap()).getContent().content();
        Assertions.assertEquals(expected, actual);
    }

    @Test
    void testProtobuf() {
        ContentCanonicalizer canonicalizer = getContentCanonicalizer(ArtifactType.PROTOBUF);

        String before = "message SearchRequest {\r\n" + "  required string query = 1;\r\n"
                + "  optional int32 page_number = 2;\r\n" + "  optional int32 result_per_page = 3;\r\n" + "}";
        String expected = "// Proto schema formatted by Wire, do not edit.\n" + "// Source: \n" + "\n"
                + "message SearchRequest {\n" + "  required string query = 1;\n" + "\n"
                + "  optional int32 page_number = 2;\n" + "\n" + "  optional int32 result_per_page = 3;\n"
                + "}\n";

        TypedContent content = toTypedContent(before, ContentTypes.APPLICATION_PROTOBUF);
        String actual = canonicalizer.canonicalize(content, Collections.emptyMap()).getContent().content();
        Assertions.assertEquals(expected, actual);
    }

    @Test
    void testGraphQL() {
        ContentCanonicalizer canonicalizer = getContentCanonicalizer(ArtifactType.GRAPHQL);

        String before = "type Query {\r\n" + "  bookById(id: ID): Book \r\n" + "}\r\n" + "\r\n"
                + "type Book {\r\n" + "    id: ID\r\n" + "  name: String\r\n" + "   pageCount: Int\r\n"
                + "  author: Author\r\n" + "}\r\n" + "\r\n" + "type Author {\r\n" + "  id: ID\r\n\r\n"
                + "    firstName: String\r\n" + "  lastName: String\r\n" + "}\r\n\r\n";
        String expected = "type Author {\n" + "  firstName: String\n" + "  id: ID\n" + "  lastName: String\n"
                + "}\n" + "\n" + "type Book {\n" + "  author: Author\n" + "  id: ID\n" + "  name: String\n"
                + "  pageCount: Int\n" + "}\n" + "\n" + "type Query {\n" + "  bookById(id: ID): Book\n"
                + "}\n" + "";

        TypedContent content = toTypedContent(before, ContentTypes.APPLICATION_GRAPHQL);
        String actual = canonicalizer.canonicalize(content, Collections.emptyMap()).getContent().content();
        Assertions.assertEquals(expected, actual);
    }

    @Test
    void testKafkaConnect() {
        ContentCanonicalizer canonicalizer = getContentCanonicalizer(ArtifactType.KCONNECT);

        String before = "{\r\n" + "    \"type\": \"struct\",\r\n" + "    \"fields\": [\r\n" + "        {\r\n"
                + "            \"type\": \"string\",\r\n" + "            \"optional\": false,\r\n"
                + "            \"field\": \"bar\"\r\n" + "        }\r\n" + "    ],\r\n"
                + "    \"optional\": false\r\n" + "}";
        String expected = "{\"fields\":[{\"field\":\"bar\",\"optional\":false,\"type\":\"string\"}],\"optional\":false,\"type\":\"struct\"}";

        TypedContent content = toTypedContent(before);
        String actual = canonicalizer.canonicalize(content, Collections.emptyMap()).getContent().content();
        Assertions.assertEquals(expected, actual);
    }

    @Test
    void testXsd() {
        ContentCanonicalizer canonicalizer = getContentCanonicalizer(ArtifactType.XSD);

        TypedContent content = resourceToTypedContentHandle("xml-schema-before.xsd");
        String expected = resourceToString("xml-schema-expected.xsd");

        String actual = canonicalizer.canonicalize(content, Collections.emptyMap()).getContent().content();
        Assertions.assertEquals(expected, actual);
    }

    @Test
    void testWsdl() {
        ContentCanonicalizer canonicalizer = getContentCanonicalizer(ArtifactType.WSDL);

        TypedContent content = resourceToTypedContentHandle("wsdl-before.wsdl");
        String expected = resourceToString("wsdl-expected.wsdl");

        String actual = canonicalizer.canonicalize(content, Collections.emptyMap()).getContent().content();
        Assertions.assertEquals(expected, actual);
    }

    @Test
    void testXml() {
        ContentCanonicalizer canonicalizer = getContentCanonicalizer(ArtifactType.XML);

        TypedContent content = resourceToTypedContentHandle("xml-before.xml");
        String expected = resourceToString("xml-expected.xml");

        String actual = canonicalizer.canonicalize(content, Collections.emptyMap()).getContent().content();
        Assertions.assertEquals(expected, actual);
    }
}
