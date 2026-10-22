package io.apicurio.registry.xml.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXParseException;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import javax.xml.parsers.DocumentBuilder;

/**
 * Tests for {@link DocumentBuilderAccessor}.
 */
public class DocumentBuilderAccessorTest {

    @Test
    void testRejectsXmlWithDoctypeDeclaration() {
        String xmlWithDoctype = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE foo [
                  <!ENTITY xxe "test">
                ]>
                <root>&xxe;</root>
                """;

        DocumentBuilder builder = DocumentBuilderAccessor.getDocumentBuilder();
        Assertions.assertThrows(SAXParseException.class, () -> {
            builder.parse(new ByteArrayInputStream(xmlWithDoctype.getBytes(StandardCharsets.UTF_8)));
        });
    }

    @Test
    void testParsesWellFormedXmlWithoutDoctype() throws Exception {
        String validXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <root><child>text</child></root>
                """;

        DocumentBuilder builder = DocumentBuilderAccessor.getDocumentBuilder();
        var document = builder.parse(
                new ByteArrayInputStream(validXml.getBytes(StandardCharsets.UTF_8)));
        Assertions.assertNotNull(document);
        Assertions.assertEquals("root", document.getDocumentElement().getTagName());
    }

}
