package io.apicurio.registry.xml.refs;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

public class XmlLocationRewriterTest {

    private static final String URL = "http://registry/apis/registry/v3/groups/g/artifacts/a/versions/1/content?references=REWRITE";
    private static final String URL_ESCAPED = URL.replace("&", "&amp;");

    @Test
    public void testRewritesOnlyMappedAttributesPreservingFormatting() {
        String in = """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <!-- see common.xsd -->
                  <xs:import namespace="urn:c"
                             schemaLocation='common.xsd' />
                  <xs:include schemaLocation="other.xsd"/>
                  <xs:annotation><xs:documentation>common.xsd</xs:documentation></xs:annotation>
                </xs:schema>
                """;
        String expected = """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <!-- see common.xsd -->
                  <xs:import namespace="urn:c"
                             schemaLocation='%s' />
                  <xs:include schemaLocation="other.xsd"/>
                  <xs:annotation><xs:documentation>common.xsd</xs:documentation></xs:annotation>
                </xs:schema>
                """.formatted(URL_ESCAPED);
        Assertions.assertEquals(expected, XmlLocationRewriter.rewrite(in, Map.of("common.xsd", URL)));
    }

    @Test
    public void testCommentedOutImportUntouched() {
        String in = "<x><!-- <xs:import schemaLocation=\"a.xsd\"/> --><xs:include schemaLocation=\"a.xsd\"/></x>";
        String out = XmlLocationRewriter.rewrite(in, Map.of("a.xsd", "u?x=1&y=2"));
        Assertions.assertEquals(
                "<x><!-- <xs:import schemaLocation=\"a.xsd\"/> --><xs:include schemaLocation=\"u?x=1&amp;y=2\"/></x>",
                out);
    }

    @Test
    public void testEscapedValueMatchedAfterUnescape() {
        String in = "<wsdl:import location=\"a.wsdl?v=1&amp;x=2\" namespace=\"urn:a\"/>";
        Assertions.assertEquals("<wsdl:import location=\"NEW\" namespace=\"urn:a\"/>",
                XmlLocationRewriter.rewrite(in, Map.of("a.wsdl?v=1&x=2", "NEW")));
    }

    @Test
    public void testQuoteEscapingInReplacement() {
        Assertions.assertEquals("<import location='a&apos;b'/>",
                XmlLocationRewriter.rewrite("<import location='x'/>", Map.of("x", "a'b")));
        Assertions.assertEquals("<import location=\"a&quot;b\"/>",
                XmlLocationRewriter.rewrite("<import location=\"x\"/>", Map.of("x", "a\"b")));
    }

    @Test
    public void testEmptyMapReturnsInputUnchanged() {
        String in = "<xs:include schemaLocation=\"a.xsd\"/>";
        Assertions.assertSame(in, XmlLocationRewriter.rewrite(in, Map.of()));
    }
}
