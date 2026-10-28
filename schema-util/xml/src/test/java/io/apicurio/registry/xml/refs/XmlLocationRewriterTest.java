package io.apicurio.registry.xml.refs;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class XmlLocationRewriterTest {

    private static final String URL = "http://registry/apis/registry/v3/groups/g/artifacts/a/versions/1/content?references=REWRITE";
    private static final String XSD = XmlReferenceKind.XSD_NS;
    private static final String WSDL11 = XmlReferenceKind.WSDL11_NS;
    private static final String WSDL20 = XmlReferenceKind.WSDL20_NS;
    private static final String XS = "<xs:schema xmlns:xs='" + XSD + "'>";
    private static final String END = "</xs:schema>";
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
        String in = "<x xmlns:xs=\"" + XSD + "\"><!-- <xs:import schemaLocation=\"a.xsd\"/> -->"
                + "<xs:include schemaLocation=\"a.xsd\"/></x>";
        String out = XmlLocationRewriter.rewrite(in, Map.of("a.xsd", "u?x=1&y=2"));
        Assertions.assertEquals(
                "<x xmlns:xs=\"" + XSD + "\"><!-- <xs:import schemaLocation=\"a.xsd\"/> -->"
                        + "<xs:include schemaLocation=\"u?x=1&amp;y=2\"/></x>",
                out);
    }

    @Test
    public void testEscapedValueMatchedAfterUnescape() {
        String in = "<wsdl:import xmlns:wsdl=\"" + WSDL11 + "\" location=\"a.wsdl?v=1&amp;x=2\" namespace=\"urn:a\"/>";
        Assertions.assertEquals("<wsdl:import xmlns:wsdl=\"" + WSDL11 + "\" location=\"NEW\" namespace=\"urn:a\"/>",
                XmlLocationRewriter.rewrite(in, Map.of("a.wsdl?v=1&x=2", "NEW")));
    }

    @Test
    public void testQuoteEscapingInReplacement() {
        String ns = "xmlns='" + WSDL11 + "' ";
        Assertions.assertEquals("<import " + ns + "location='a&apos;b'/>",
                XmlLocationRewriter.rewrite("<import " + ns + "location='x'/>", Map.of("x", "a'b")));
        Assertions.assertEquals("<import " + ns + "location=\"a&quot;b\"/>",
                XmlLocationRewriter.rewrite("<import " + ns + "location=\"x\"/>", Map.of("x", "a\"b")));
    }

    @Test
    public void testEmptyMapReturnsInputUnchanged() {
        String in = "<xs:include schemaLocation=\"a.xsd\"/>";
        Assertions.assertSame(in, XmlLocationRewriter.rewrite(in, Map.of()));
    }

    @Test
    public void testGreaterThanInsidePrecedingQuotedAttribute() {
        String in = XS + "<xs:import ext:note='1 > 0' schemaLocation='common.xsd'/>" + END;
        Assertions.assertEquals(XS + "<xs:import ext:note='1 > 0' schemaLocation='NEW'/>" + END,
                XmlLocationRewriter.rewrite(in, Map.of("common.xsd", "NEW")));
    }

    @Test
    public void testLocationTextInsideOtherAttributeUntouched() {
        String in = XS + "<xs:import ext:note=\" schemaLocation='common.xsd' \"/>" + END;
        Assertions.assertEquals(in, XmlLocationRewriter.rewrite(in, Map.of("common.xsd", "NEW")));
    }

    @Test
    public void testForeignNamespaceImportUntouched() {
        String in = XS.replace(">", " xmlns:ext='urn:ext'>")
                + "<ext:import location='common.xsd'/><ext:import schemaLocation='common.xsd'/>"
                + "<xs:import schemaLocation='common.xsd'/>" + END;
        String expected = XS.replace(">", " xmlns:ext='urn:ext'>")
                + "<ext:import location='common.xsd'/><ext:import schemaLocation='common.xsd'/>"
                + "<xs:import schemaLocation='NEW'/>" + END;
        Assertions.assertEquals(expected, XmlLocationRewriter.rewrite(in, Map.of("common.xsd", "NEW")));
    }

    @Test
    public void testDefaultNamespaceElement() {
        String in = "<schema xmlns=\"" + XSD + "\"><import schemaLocation=\"common.xsd\"/></schema>";
        Assertions.assertEquals(in.replace("common.xsd", "NEW"),
                XmlLocationRewriter.rewrite(in, Map.of("common.xsd", "NEW")));
    }

    @Test
    public void testPrefixRedeclaredOnChild() {
        String in = XS + "<xs:annotation xmlns:xs='urn:other'><xs:import schemaLocation='a.xsd'/></xs:annotation>"
                + "<xs:import schemaLocation='a.xsd'/>" + END;
        String expected = XS + "<xs:annotation xmlns:xs='urn:other'><xs:import schemaLocation='a.xsd'/></xs:annotation>"
                + "<xs:import schemaLocation='NEW'/>" + END;
        Assertions.assertEquals(expected, XmlLocationRewriter.rewrite(in, Map.of("a.xsd", "NEW")));
    }

    @Test
    public void testWsdl11AndWsdl20() {
        String w11 = "<d:definitions xmlns:d='" + WSDL11 + "'><d:import location='a.wsdl' namespace='urn:a'/></d:definitions>";
        Assertions.assertEquals(w11.replace("a.wsdl", "NEW"),
                XmlLocationRewriter.rewrite(w11, Map.of("a.wsdl", "NEW")));
        String w20 = "<description xmlns='" + WSDL20 + "'><import location='a.wsdl'/><include location='b.wsdl'/></description>";
        Assertions.assertEquals(w20.replace("a.wsdl", "NEW").replace("b.wsdl", "NEW2"),
                XmlLocationRewriter.rewrite(w20, Map.of("a.wsdl", "NEW", "b.wsdl", "NEW2")));
    }

    @Test
    public void testCharacterReferencesAndPaddingDecoded() {
        String in = XS + "<xs:import schemaLocation='common&#x2e;xsd'/><xs:include schemaLocation=\"common&#46;xsd\"/>"
                + "<xs:redefine schemaLocation=' pad.xsd '/>" + END;
        String expected = XS + "<xs:import schemaLocation='NEW'/><xs:include schemaLocation=\"NEW\"/>"
                + "<xs:redefine schemaLocation='P'/>" + END;
        Assertions.assertEquals(expected,
                XmlLocationRewriter.rewrite(in, Map.of("common.xsd", "NEW", "pad.xsd", "P")));
    }

    @Test
    public void testNoDoubleDecoding() {
        String in = XS + "<xs:import schemaLocation='common&amp;#x2e;xsd'/>" + END;
        Assertions.assertEquals(in, XmlLocationRewriter.rewrite(in, Map.of("common.xsd", "NEW")));
        Assertions.assertEquals(XS + "<xs:import schemaLocation='NEW'/>" + END,
                XmlLocationRewriter.rewrite(in, Map.of("common&#x2e;xsd", "NEW")));
    }

    @Test
    public void testCommentAndCdataImportsUntouched() {
        String in = XS + "<!-- <xs:import schemaLocation='a.xsd'/> -->"
                + "<xs:annotation><xs:documentation><![CDATA[<xs:import schemaLocation='a.xsd'/>]]>"
                + "</xs:documentation></xs:annotation>" + END;
        Assertions.assertEquals(in, XmlLocationRewriter.rewrite(in, Map.of("a.xsd", "NEW")));
    }

    @Test
    public void testWrongAttributeForKindUntouched() {
        String in = XS + "<xs:import location='a.xsd'/>" + END;
        Assertions.assertEquals(in, XmlLocationRewriter.rewrite(in, Map.of("a.xsd", "NEW")));
    }

    @Test
    public void testDoctypeAndPiSkipped() {
        String in = "<?xml version='1.0'?><!DOCTYPE x [<!ENTITY e \"<xs:import schemaLocation='a.xsd'/>\">]>"
                + XS + "<?pi <xs:import schemaLocation='a.xsd'/> ?><xs:import schemaLocation='a.xsd'/>" + END;
        String expected = in.substring(0, in.lastIndexOf("a.xsd")) + "NEW" + in.substring(in.lastIndexOf("a.xsd") + 5);
        Assertions.assertEquals(expected, XmlLocationRewriter.rewrite(in, Map.of("a.xsd", "NEW")));
    }

    @Test
    public void testMalformedInputDoesNotThrow() {
        Map<String, String> map = Map.of("a.xsd", "NEW");
        for (String in : List.of("<", "<xs:import schemaLocation='a.xsd", "<!-- x", "<![CDATA[", "<?x", "<!DOCTYPE [",
                "</", "<a b='>", XS + "<xs:import schemaLocation=a.xsd/>")) {
            Assertions.assertEquals(in, XmlLocationRewriter.rewrite(in, map));
        }
    }

    @Test
    public void testConsistentWithLocator() {
        List<String> docs = List.of(
                XS.replace(">", " xmlns:ext='urn:ext'>") + "<xs:import ext:note='1 > 0' schemaLocation='common&#x2e;xsd'/>"
                        + "<xs:include schemaLocation=' pad.xsd '/><xs:redefine schemaLocation='a&amp;b.xsd'/>" + END,
                "<schema xmlns='" + XSD + "'><import schemaLocation='x&#46;xsd'/><override schemaLocation='o.xsd'/></schema>",
                "<w:definitions xmlns:w='" + WSDL11 + "'><w:import location='&quot;q&quot;.wsdl'/></w:definitions>",
                "<description xmlns='" + WSDL20 + "'><import location='i.wsdl'/><include location='j.wsdl'/></description>");
        for (String doc : docs) {
            List<XmlReference> refs = XmlReferenceLocator.locate(doc);
            Assertions.assertFalse(refs.isEmpty());
            Map<String, String> map = new HashMap<>();
            for (int i = 0; i < refs.size(); i++) {
                map.put(refs.get(i).location(), "urn:new:" + i);
            }
            String out = XmlLocationRewriter.rewrite(doc, map);
            List<String> rewritten = XmlReferenceLocator.locate(out).stream().map(XmlReference::location).toList();
            Assertions.assertEquals(map.values().stream().sorted().toList(), rewritten.stream().sorted().toList(), doc);
        }
    }
}
