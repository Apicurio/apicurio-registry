package io.apicurio.registry.content.accepter;

import io.apicurio.registry.asyncapi.content.AsyncApiContentAccepter;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.graphql.content.GraphQLContentAccepter;
import io.apicurio.registry.openapi.content.OpenApiContentAccepter;
import io.apicurio.registry.wsdl.content.WsdlContentAccepter;
import io.apicurio.registry.xml.content.XmlContentAccepter;
import io.apicurio.registry.xsd.content.XsdContentAccepter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;

public class ContentAccepterNullContentTypeTest {

    @Test
    public void testOpenApiContentAccepter_nullContentType() {
        String openApiJson = "{\n" +
                "  \"openapi\": \"3.0.0\",\n" +
                "  \"info\": {\n" +
                "    \"title\": \"Sample API\",\n" +
                "    \"version\": \"0.1.0\"\n" +
                "  },\n" +
                "  \"paths\": {}\n" +
                "}";
        TypedContent content = TypedContent.create(ContentHandle.create(openApiJson), null);
        OpenApiContentAccepter accepter = new OpenApiContentAccepter();

        Assertions.assertTrue(accepter.acceptsContent(content, Collections.emptyMap()));
    }

    @Test
    public void testAsyncApiContentAccepter_nullContentType() {
        String asyncApiJson = "{\n" +
                "  \"asyncapi\": \"2.0.0\",\n" +
                "  \"info\": {\n" +
                "    \"title\": \"Sample AsyncAPI\",\n" +
                "    \"version\": \"0.1.0\"\n" +
                "  }\n" +
                "}";
        TypedContent content = TypedContent.create(ContentHandle.create(asyncApiJson), null);
        AsyncApiContentAccepter accepter = new AsyncApiContentAccepter();

        Assertions.assertTrue(accepter.acceptsContent(content, Collections.emptyMap()));
    }

    @Test
    public void testXmlContentAccepter_nullContentType() {
        TypedContent content = TypedContent.create(ContentHandle.create("<root/>"), null);
        XmlContentAccepter accepter = new XmlContentAccepter();

        Assertions.assertFalse(accepter.acceptsContent(content, Collections.emptyMap()));
    }

    @Test
    public void testXsdContentAccepter_nullContentType() {
        TypedContent content = TypedContent.create(ContentHandle.create("<schema xmlns=\"http://www.w3.org/2001/XMLSchema\"/>"), null);
        XsdContentAccepter accepter = new XsdContentAccepter();

        Assertions.assertFalse(accepter.acceptsContent(content, Collections.emptyMap()));
    }

    @Test
    public void testWsdlContentAccepter_nullContentType() {
        TypedContent content = TypedContent.create(ContentHandle.create("<definitions xmlns=\"http://schemas.xmlsoap.org/wsdl/\"/>"), null);
        WsdlContentAccepter accepter = new WsdlContentAccepter();

        Assertions.assertFalse(accepter.acceptsContent(content, Collections.emptyMap()));
    }

    @Test
    public void testGraphQLContentAccepter_nullContentType() {
        TypedContent content = TypedContent.create(ContentHandle.create("type Query { hello: String }"), null);
        GraphQLContentAccepter accepter = new GraphQLContentAccepter();

        Assertions.assertFalse(accepter.acceptsContent(content, Collections.emptyMap()));
    }
}
