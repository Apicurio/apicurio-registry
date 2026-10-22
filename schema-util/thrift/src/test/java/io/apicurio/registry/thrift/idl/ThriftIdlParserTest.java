package io.apicurio.registry.thrift.idl;

import io.apicurio.registry.thrift.idl.ThriftIdlParser.ThriftDocument;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

public class ThriftIdlParserTest {

    @Test
    public void testParseSimpleStruct() throws ThriftIdlParseException {
        ThriftDocument doc = ThriftIdlParser.parse(loadResource("simple.thrift"));
        Assertions.assertEquals(1, doc.getNamespaces().size());
        Assertions.assertEquals("java", doc.getNamespaces().get(0).getLanguage());
        Assertions.assertEquals("com.example.simple", doc.getNamespaces().get(0).getName());
        Assertions.assertEquals(1, doc.getStructs().size());
        Assertions.assertEquals("Person", doc.getStructs().get(0));
    }

    @Test
    public void testParseComplexSchema() throws ThriftIdlParseException {
        ThriftDocument doc = ThriftIdlParser.parse(loadResource("complex.thrift"));
        Assertions.assertEquals(2, doc.getNamespaces().size());
        Assertions.assertEquals(1, doc.getIncludes().size());
        Assertions.assertEquals("shared.thrift", doc.getIncludes().get(0));
        Assertions.assertEquals(2, doc.getConstants().size());
        Assertions.assertEquals(2, doc.getTypedefs().size());
        Assertions.assertEquals(1, doc.getEnums().size());
        Assertions.assertEquals("Priority", doc.getEnums().get(0));
        Assertions.assertEquals(2, doc.getStructs().size());
        Assertions.assertTrue(doc.getStructs().contains("Request"));
        Assertions.assertTrue(doc.getStructs().contains("Response"));
        Assertions.assertEquals(1, doc.getUnions().size());
        Assertions.assertEquals("Result", doc.getUnions().get(0));
        Assertions.assertEquals(2, doc.getExceptions().size());
        Assertions.assertTrue(doc.getExceptions().contains("ServiceException"));
        Assertions.assertTrue(doc.getExceptions().contains("TimeoutException"));
        Assertions.assertEquals(2, doc.getServices().size());
        Assertions.assertTrue(doc.getServices().contains("HttpService"));
        Assertions.assertTrue(doc.getServices().contains("ExtendedHttpService"));
    }

    @Test
    public void testParseFb303() throws ThriftIdlParseException {
        ThriftDocument doc = ThriftIdlParser.parse(loadResource("fb303.thrift"));
        Assertions.assertEquals(3, doc.getNamespaces().size());
        Assertions.assertEquals(1, doc.getEnums().size());
        Assertions.assertEquals("fb_status", doc.getEnums().get(0));
        Assertions.assertEquals(1, doc.getServices().size());
        Assertions.assertEquals("FacebookService", doc.getServices().get(0));
    }

    @Test
    public void testParseWithComments() throws ThriftIdlParseException {
        ThriftDocument doc = ThriftIdlParser.parse(loadResource("with-comments.thrift"));
        Assertions.assertEquals(1, doc.getNamespaces().size());
        Assertions.assertEquals(1, doc.getStructs().size());
        Assertions.assertEquals("Data", doc.getStructs().get(0));
    }

    @Test
    public void testEmptyContentThrows() {
        Assertions.assertThrows(ThriftIdlParseException.class, () -> ThriftIdlParser.parse(""));
        Assertions.assertThrows(ThriftIdlParseException.class, () -> ThriftIdlParser.parse("   "));
        Assertions.assertThrows(ThriftIdlParseException.class, () -> ThriftIdlParser.parse(null));
    }

    @Test
    public void testInvalidContentThrows() {
        Assertions.assertThrows(ThriftIdlParseException.class,
                () -> ThriftIdlParser.parse("This is just plain text with no Thrift constructs."));
    }

    @Test
    public void testIsThriftIdlValid() {
        Assertions.assertTrue(ThriftIdlParser.isThriftIdl(loadResource("simple.thrift")));
        Assertions.assertTrue(ThriftIdlParser.isThriftIdl(loadResource("complex.thrift")));
        Assertions.assertTrue(ThriftIdlParser.isThriftIdl(loadResource("fb303.thrift")));
    }

    @Test
    public void testIsThriftIdlInvalid() {
        Assertions.assertFalse(ThriftIdlParser.isThriftIdl("random content"));
        Assertions.assertFalse(ThriftIdlParser.isThriftIdl(""));
        Assertions.assertFalse(ThriftIdlParser.isThriftIdl(null));
    }

    @Test
    public void testStripComments() {
        String input = "// line comment\nnamespace java test\n/* block */\nstruct Foo {\n}\n";
        String stripped = ThriftIdlParser.stripComments(input);
        Assertions.assertFalse(stripped.contains("line comment"));
        Assertions.assertFalse(stripped.contains("block"));
        Assertions.assertTrue(stripped.contains("namespace java test"));
        Assertions.assertTrue(stripped.contains("struct Foo"));
    }

    @Test
    public void testStripShellComments() {
        String input = "# shell comment\nnamespace java test\n";
        String stripped = ThriftIdlParser.stripComments(input);
        Assertions.assertFalse(stripped.contains("shell comment"));
        Assertions.assertTrue(stripped.contains("namespace java test"));
    }

    @Test
    public void testNamespaceOnly() throws ThriftIdlParseException {
        ThriftDocument doc = ThriftIdlParser.parse("namespace java com.example");
        Assertions.assertEquals(1, doc.getNamespaces().size());
    }

    @Test
    public void testConstParsing() throws ThriftIdlParseException {
        String content = "const i32 MAX_VALUE = 100\nconst string NAME = \"test\"";
        ThriftDocument doc = ThriftIdlParser.parse(content);
        Assertions.assertEquals(2, doc.getConstants().size());
        Assertions.assertEquals("MAX_VALUE", doc.getConstants().get(0).getName());
        Assertions.assertEquals("i32", doc.getConstants().get(0).getType());
        Assertions.assertEquals("NAME", doc.getConstants().get(1).getName());
        Assertions.assertEquals("string", doc.getConstants().get(1).getType());
    }

    @Test
    public void testTypedefParsing() throws ThriftIdlParseException {
        String content = "typedef i64 Timestamp\ntypedef string UserId";
        ThriftDocument doc = ThriftIdlParser.parse(content);
        Assertions.assertEquals(2, doc.getTypedefs().size());
        Assertions.assertEquals("Timestamp", doc.getTypedefs().get(0).getName());
        Assertions.assertEquals("i64", doc.getTypedefs().get(0).getType());
    }

    @Test
    public void testTypedefWithGenericType() throws ThriftIdlParseException {
        String content = "typedef map<string, string> Headers\n"
                + "typedef map<string, list<i32>> ComplexType";
        ThriftDocument doc = ThriftIdlParser.parse(content);
        Assertions.assertEquals(2, doc.getTypedefs().size());
        Assertions.assertEquals("Headers", doc.getTypedefs().get(0).getName());
        Assertions.assertEquals("map<string, string>", doc.getTypedefs().get(0).getType());
        Assertions.assertEquals("ComplexType", doc.getTypedefs().get(1).getName());
        Assertions.assertEquals("map<string, list<i32>>", doc.getTypedefs().get(1).getType());
    }

    @Test
    public void testConstWithGenericType() throws ThriftIdlParseException {
        String content = "const map<string, string> MAPCONSTANT = {'hello': 'world'}";
        ThriftDocument doc = ThriftIdlParser.parse(content);
        Assertions.assertEquals(1, doc.getConstants().size());
        Assertions.assertEquals("MAPCONSTANT", doc.getConstants().get(0).getName());
        Assertions.assertEquals("map<string, string>", doc.getConstants().get(0).getType());
    }

    @Test
    public void testSenumParsing() throws ThriftIdlParseException {
        String content = "senum Colors {\n  \"RED\",\n  \"GREEN\",\n  \"BLUE\"\n}";
        ThriftDocument doc = ThriftIdlParser.parse(content);
        Assertions.assertEquals(1, doc.getEnums().size());
        Assertions.assertEquals("Colors", doc.getEnums().get(0));
    }

    private String loadResource(String name) {
        try (InputStream is = getClass().getResourceAsStream(name)) {
            Assertions.assertNotNull(is, "Resource not found: " + name);
            return new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8)).lines()
                    .collect(Collectors.joining("\n"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
