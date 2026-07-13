package io.apicurio.registry.content.util;

import io.apicurio.registry.content.ContentHandle;
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
    public void testIsParsableGraphQL() {
        ContentHandle content = ContentHandle.create("type Query { hello: String }");
        Assertions.assertTrue(ContentTypeUtil.isParsableGraphQL(content));
        
        ContentHandle content2 = ContentHandle.create("schema { query: Query }");
        Assertions.assertTrue(ContentTypeUtil.isParsableGraphQL(content2));

        ContentHandle notGraphql = ContentHandle.create("message Hello { string msg = 1; }");
        Assertions.assertFalse(ContentTypeUtil.isParsableGraphQL(notGraphql));
    }

    @Test
    public void testDetermineContentType_GraphQL() {
        ContentHandle content = ContentHandle.create("type Query { hello: String }");
        Assertions.assertEquals(io.apicurio.registry.types.ContentTypes.APPLICATION_GRAPHQL, ContentTypeUtil.determineContentType(content));
    }
}
