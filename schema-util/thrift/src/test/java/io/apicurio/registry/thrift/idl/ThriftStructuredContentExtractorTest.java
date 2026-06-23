package io.apicurio.registry.thrift.idl;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.extract.StructuredElement;
import io.apicurio.registry.thrift.content.extract.ThriftStructuredContentExtractor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class ThriftStructuredContentExtractorTest {

    private final ThriftStructuredContentExtractor extractor = new ThriftStructuredContentExtractor();

    @Test
    public void testExtractsStructNames() {
        String schema = "namespace java com.example\nstruct Person {\n  1: string name\n}\n"
                + "struct Address {\n  1: string street\n}\n";
        List<StructuredElement> elements = extractor.extract(ContentHandle.create(schema));

        Assertions.assertTrue(elements.stream()
                .anyMatch(e -> "struct".equals(e.kind()) && "Person".equals(e.name())));
        Assertions.assertTrue(elements.stream()
                .anyMatch(e -> "struct".equals(e.kind()) && "Address".equals(e.name())));
    }

    @Test
    public void testExtractsServiceNames() {
        String schema = "service Calculator {\n  i32 add(1: i32 a, 2: i32 b)\n}\n";
        List<StructuredElement> elements = extractor.extract(ContentHandle.create(schema));

        Assertions.assertTrue(elements.stream()
                .anyMatch(e -> "service".equals(e.kind()) && "Calculator".equals(e.name())));
    }

    @Test
    public void testExtractsEnumNames() {
        String schema = "enum Color {\n  RED = 1,\n  GREEN = 2\n}\n";
        List<StructuredElement> elements = extractor.extract(ContentHandle.create(schema));

        Assertions.assertTrue(elements.stream()
                .anyMatch(e -> "enum".equals(e.kind()) && "Color".equals(e.name())));
    }

    @Test
    public void testExtractsUnionNames() {
        String schema = "union Result {\n  1: string success,\n  2: string error\n}\n";
        List<StructuredElement> elements = extractor.extract(ContentHandle.create(schema));

        Assertions.assertTrue(elements.stream()
                .anyMatch(e -> "union".equals(e.kind()) && "Result".equals(e.name())));
    }

    @Test
    public void testExtractsExceptionNames() {
        String schema = "exception NotFound {\n  1: string message\n}\n";
        List<StructuredElement> elements = extractor.extract(ContentHandle.create(schema));

        Assertions.assertTrue(elements.stream()
                .anyMatch(e -> "exception".equals(e.kind()) && "NotFound".equals(e.name())));
    }

    @Test
    public void testExtractsNamespaces() {
        String schema = "namespace java com.example.service\nstruct Foo {\n  1: string bar\n}\n";
        List<StructuredElement> elements = extractor.extract(ContentHandle.create(schema));

        Assertions.assertTrue(elements.stream()
                .anyMatch(e -> "namespace".equals(e.kind()) && "com.example.service".equals(e.name())));
    }

    @Test
    public void testExtractsTypedefNames() {
        String schema = "typedef i64 Timestamp\ntypedef string UserId\nstruct Foo {\n  1: Timestamp t\n}\n";
        List<StructuredElement> elements = extractor.extract(ContentHandle.create(schema));

        Assertions.assertTrue(elements.stream()
                .anyMatch(e -> "typedef".equals(e.kind()) && "Timestamp".equals(e.name())));
        Assertions.assertTrue(elements.stream()
                .anyMatch(e -> "typedef".equals(e.kind()) && "UserId".equals(e.name())));
    }

    @Test
    public void testExtractsConstNames() {
        String schema = "const i32 MAX = 100\nstruct Foo {\n  1: i32 val\n}\n";
        List<StructuredElement> elements = extractor.extract(ContentHandle.create(schema));

        Assertions.assertTrue(elements.stream()
                .anyMatch(e -> "const".equals(e.kind()) && "MAX".equals(e.name())));
    }

    @Test
    public void testReturnsEmptyForInvalidContent() {
        List<StructuredElement> elements = extractor.extract(ContentHandle.create("not thrift"));
        Assertions.assertTrue(elements.isEmpty());
    }

    @Test
    public void testComplexSchemaExtraction() {
        String schema = "namespace java com.example\n"
                + "enum Status { ACTIVE = 1, INACTIVE = 2 }\n"
                + "struct User {\n  1: string name\n}\n"
                + "union Response {\n  1: User user,\n  2: string error\n}\n"
                + "exception AuthError {\n  1: string reason\n}\n"
                + "service UserService {\n  User getUser(1: string id)\n}\n";

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(schema));

        Assertions.assertEquals(6, elements.size());
        Assertions.assertTrue(elements.stream().anyMatch(e -> "namespace".equals(e.kind())));
        Assertions.assertTrue(elements.stream().anyMatch(e -> "enum".equals(e.kind())));
        Assertions.assertTrue(elements.stream().anyMatch(e -> "struct".equals(e.kind())));
        Assertions.assertTrue(elements.stream().anyMatch(e -> "union".equals(e.kind())));
        Assertions.assertTrue(elements.stream().anyMatch(e -> "exception".equals(e.kind())));
        Assertions.assertTrue(elements.stream().anyMatch(e -> "service".equals(e.kind())));
    }

}
