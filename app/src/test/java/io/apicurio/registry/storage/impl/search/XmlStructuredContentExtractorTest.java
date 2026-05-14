package io.apicurio.registry.storage.impl.search;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.extract.StructuredElement;
import io.apicurio.registry.xml.content.extract.XmlStructuredContentExtractor;

import static org.junit.jupiter.api.Assertions.assertTrue;

class XmlStructuredContentExtractorTest {

    private final XmlStructuredContentExtractor extractor = new XmlStructuredContentExtractor();

    @Test
    void testExtractRootElement() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <catalog>
                    <book>
                        <title>Test</title>
                    </book>
                </catalog>
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(xml));

        assertTrue(
                elements.stream().anyMatch(e -> e.kind().equals("element") && e.name().equals("catalog")));
    }

    @Test
    void testExtractChildElements() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <library>
                    <book>
                        <title>Book One</title>
                    </book>
                    <magazine>
                        <title>Mag One</title>
                    </magazine>
                    <dvd>
                        <title>DVD One</title>
                    </dvd>
                </library>
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(xml));

        assertTrue(
                elements.stream().anyMatch(e -> e.kind().equals("element") && e.name().equals("library")));
        assertTrue(
                elements.stream().anyMatch(e -> e.kind().equals("element") && e.name().equals("book")));
        assertTrue(
                elements.stream().anyMatch(e -> e.kind().equals("element") && e.name().equals("magazine")));
        assertTrue(
                elements.stream().anyMatch(e -> e.kind().equals("element") && e.name().equals("dvd")));
    }

    @Test
    void testExtractNamespaces() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <root xmlns="http://example.com/default"
                      xmlns:custom="http://example.com/custom">
                    <child>test</child>
                    <custom:item>value</custom:item>
                </root>
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(xml));

        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("namespace") && e.name().equals("http://example.com/default")));
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("namespace") && e.name().equals("http://example.com/custom")));
    }

    @Test
    void testExtractNamespacedRootElement() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <ns:config xmlns:ns="http://example.com/config">
                    <ns:setting>value</ns:setting>
                </ns:config>
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(xml));

        assertTrue(
                elements.stream().anyMatch(e -> e.kind().equals("element") && e.name().equals("config")));
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("namespace") && e.name().equals("http://example.com/config")));
    }

    @Test
    void testDeduplicatedChildElements() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <items>
                    <item>one</item>
                    <item>two</item>
                    <item>three</item>
                </items>
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(xml));

        // "item" should appear only once (deduplicated)
        long itemCount = elements.stream()
                .filter(e -> e.kind().equals("element") && e.name().equals("item"))
                .count();
        assertTrue(itemCount == 1, "Expected 'item' element to appear exactly once but found " + itemCount);
    }

    @Test
    void testComprehensiveXml() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <order xmlns="http://example.com/orders"
                       xmlns:addr="http://example.com/address">
                    <customer>John Doe</customer>
                    <addr:shipping>
                        <addr:street>123 Main St</addr:street>
                    </addr:shipping>
                    <items>
                        <item>Widget</item>
                    </items>
                    <total>99.99</total>
                </order>
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(xml));

        // Root element
        assertTrue(
                elements.stream().anyMatch(e -> e.kind().equals("element") && e.name().equals("order")));

        // Namespaces
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("namespace") && e.name().equals("http://example.com/orders")));
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("namespace") && e.name().equals("http://example.com/address")));

        // Direct child elements
        assertTrue(
                elements.stream().anyMatch(e -> e.kind().equals("element") && e.name().equals("customer")));
        assertTrue(
                elements.stream().anyMatch(e -> e.kind().equals("element") && e.name().equals("shipping")));
        assertTrue(
                elements.stream().anyMatch(e -> e.kind().equals("element") && e.name().equals("items")));
        assertTrue(
                elements.stream().anyMatch(e -> e.kind().equals("element") && e.name().equals("total")));
    }

    @Test
    void testExtractFromInvalidContent() {
        List<StructuredElement> elements = extractor.extract(ContentHandle.create("not valid xml <><>"));

        assertTrue(elements.isEmpty());
    }

    @Test
    void testSimpleXmlNoNamespace() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <person>
                    <name>Jane</name>
                    <age>30</age>
                </person>
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(xml));

        assertTrue(
                elements.stream().anyMatch(e -> e.kind().equals("element") && e.name().equals("person")));
        assertTrue(
                elements.stream().anyMatch(e -> e.kind().equals("element") && e.name().equals("name")));
        assertTrue(
                elements.stream().anyMatch(e -> e.kind().equals("element") && e.name().equals("age")));
        // No namespace elements
        assertTrue(elements.stream().noneMatch(e -> e.kind().equals("namespace")));
    }
}
