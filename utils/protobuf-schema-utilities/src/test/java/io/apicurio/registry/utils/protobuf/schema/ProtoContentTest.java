package io.apicurio.registry.utils.protobuf.schema;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ProtoContentTest {

    @Test
    public void testGetImportPath() {
        ProtoContent protoContent = new ProtoContent("test.proto", "syntax = \"proto3\";");
        assertEquals("test.proto", protoContent.getImportPath());
    }

    @Test
    public void testGetFilename() {
        ProtoContent protoContent = new ProtoContent("test.proto", "syntax = \"proto3\";");
        assertEquals("test.proto", protoContent.getFilename());
    }

    @Test
    public void testGetFilenameFromPath() {
        ProtoContent protoContent = new ProtoContent("com/example/test.proto", "syntax = \"proto3\";");
        assertEquals("test.proto", protoContent.getFilename());
    }

    @Test
    public void testGetFilenameFromDeepPath() {
        ProtoContent protoContent = new ProtoContent("com/example/nested/deep/test.proto", "syntax = \"proto3\";");
        assertEquals("test.proto", protoContent.getFilename());
    }

    @Test
    public void testGetContent() {
        String content = "syntax = \"proto3\";";
        ProtoContent protoContent = new ProtoContent("test.proto", content);
        assertEquals(content, protoContent.getContent());
    }

    @Test
    public void testGetPackageNameWithPackage() {
        String content = "syntax = \"proto3\";\npackage com.example.test;\nmessage Test {}";
        ProtoContent protoContent = new ProtoContent("test.proto", content);
        assertEquals("com.example.test", protoContent.getPackageName());
    }

    @Test
    public void testGetPackageNameWithoutPackage() {
        String content = "syntax = \"proto3\";\nmessage Test {}";
        ProtoContent protoContent = new ProtoContent("test.proto", content);
        assertEquals("", protoContent.getPackageName());
    }

    @Test
    public void testGetPackageNameWithWhitespace() {
        String content = "syntax = \"proto3\";\n  package   com.example.test  ;\nmessage Test {}";
        ProtoContent protoContent = new ProtoContent("test.proto", content);
        assertEquals("com.example.test", protoContent.getPackageName());
    }

    @Test
    public void testGetPackageNameWithComments() {
        String content = "syntax = \"proto3\";\n// This is a comment\npackage com.example.test;\n/* Another comment */\nmessage Test {}";
        ProtoContent protoContent = new ProtoContent("test.proto", content);
        assertEquals("com.example.test", protoContent.getPackageName());
    }

    @Test
    public void testGetPackageNameFirstPackageWins() {
        String content = "syntax = \"proto3\";\npackage com.example.first;\npackage com.example.second;\nmessage Test {}";
        ProtoContent protoContent = new ProtoContent("test.proto", content);
        assertEquals("com.example.first", protoContent.getPackageName());
    }

    @Test
    public void testGetPackageNameSingleWord() {
        String content = "syntax = \"proto3\";\npackage test;\nmessage Test {}";
        ProtoContent protoContent = new ProtoContent("test.proto", content);
        assertEquals("test", protoContent.getPackageName());
    }

    @Test
    public void testGetExpectedImportPathWithPackage() {
        String content = "syntax = \"proto3\";\npackage com.example.test;\nmessage Test {}";
        ProtoContent protoContent = new ProtoContent("test.proto", content);
        assertEquals("com/example/test/test.proto", protoContent.getExpectedImportPath());
    }

    @Test
    public void testGetExpectedImportPathWithoutPackage() {
        String content = "syntax = \"proto3\";\nmessage Test {}";
        ProtoContent protoContent = new ProtoContent("test.proto", content);
        assertEquals("test.proto", protoContent.getExpectedImportPath());
    }

    @Test
    public void testGetExpectedImportPathWithSingleWordPackage() {
        String content = "syntax = \"proto3\";\npackage test;\nmessage Test {}";
        ProtoContent protoContent = new ProtoContent("test.proto", content);
        assertEquals("test/test.proto", protoContent.getExpectedImportPath());
    }

    @Test
    public void testGetExpectedImportPathFromDeepImportPath() {
        String content = "syntax = \"proto3\";\npackage com.example.proto;\nmessage Test {}";
        ProtoContent protoContent = new ProtoContent("wrong/path/to/test.proto", content);
        assertEquals("com/example/proto/test.proto", protoContent.getExpectedImportPath());
    }

    @Test
    public void testIsImportPathMismatchedWhenMatched() {
        String content = "syntax = \"proto3\";\npackage com.example.test;\nmessage Test {}";
        ProtoContent protoContent = new ProtoContent("com/example/test/test.proto", content);
        assertEquals(false, protoContent.isImportPathMismatched());
    }

    @Test
    public void testIsImportPathMismatchedWhenMismatched() {
        String content = "syntax = \"proto3\";\npackage com.example.test;\nmessage Test {}";
        ProtoContent protoContent = new ProtoContent("wrong/path/test.proto", content);
        assertEquals(true, protoContent.isImportPathMismatched());
    }

    @Test
    public void testIsImportPathMismatchedNoPackage() {
        String content = "syntax = \"proto3\";\nmessage Test {}";
        ProtoContent protoContent = new ProtoContent("test.proto", content);
        assertEquals(false, protoContent.isImportPathMismatched());
    }

    @Test
    public void testIsImportPathMismatchedNoPackageMismatched() {
        String content = "syntax = \"proto3\";\nmessage Test {}";
        ProtoContent protoContent = new ProtoContent("wrong/path/test.proto", content);
        assertEquals(true, protoContent.isImportPathMismatched());
    }

    @Test
    public void testFixImportBasic() {
        String content = "syntax = \"proto3\";\nimport \"old/path/file.proto\";\nmessage Test {}";
        ProtoContent protoContent = new ProtoContent("test.proto", content);
        protoContent.fixImport("old/path/file.proto", "new/path/file.proto");
        assertEquals("syntax = \"proto3\";\nimport \"new/path/file.proto\";\nmessage Test {}", protoContent.getContent());
    }

    @Test
    public void testFixImportWithWhitespace() {
        String content = "syntax = \"proto3\";\n  import   \"old/path/file.proto\";\nmessage Test {}";
        ProtoContent protoContent = new ProtoContent("test.proto", content);
        protoContent.fixImport("old/path/file.proto", "new/path/file.proto");
        assertEquals("syntax = \"proto3\";\n  import   \"new/path/file.proto\";\nmessage Test {}", protoContent.getContent());
    }

    @Test
    public void testFixImportMultipleOccurrences() {
        String content = "syntax = \"proto3\";\nimport \"old/path/file.proto\";\nimport \"other.proto\";\nimport \"old/path/file.proto\";\nmessage Test {}";
        ProtoContent protoContent = new ProtoContent("test.proto", content);
        protoContent.fixImport("old/path/file.proto", "new/path/file.proto");
        assertEquals("syntax = \"proto3\";\nimport \"new/path/file.proto\";\nimport \"other.proto\";\nimport \"new/path/file.proto\";\nmessage Test {}", protoContent.getContent());
    }

    @Test
    public void testFixImportNoMatch() {
        String content = "syntax = \"proto3\";\nimport \"other/path/file.proto\";\nmessage Test {}";
        ProtoContent protoContent = new ProtoContent("test.proto", content);
        protoContent.fixImport("old/path/file.proto", "new/path/file.proto");
        assertEquals("syntax = \"proto3\";\nimport \"other/path/file.proto\";\nmessage Test {}", protoContent.getContent());
    }

    @Test
    public void testFixImportWithSpecialCharacters() {
        String content = "syntax = \"proto3\";\nimport \"path/with.dots_and-dashes/file.proto\";\nmessage Test {}";
        ProtoContent protoContent = new ProtoContent("test.proto", content);
        protoContent.fixImport("path/with.dots_and-dashes/file.proto", "new/path/file.proto");
        assertEquals("syntax = \"proto3\";\nimport \"new/path/file.proto\";\nmessage Test {}", protoContent.getContent());
    }

    @Test
    public void testFixImportWithPublicModifier() {
        String content = "syntax = \"proto3\";\nimport public \"old/path/file.proto\";\nmessage Test {}";
        ProtoContent protoContent = new ProtoContent("test.proto", content);
        protoContent.fixImport("old/path/file.proto", "new/path/file.proto");
        assertEquals("syntax = \"proto3\";\nimport public \"new/path/file.proto\";\nmessage Test {}", protoContent.getContent());
    }

    @Test
    public void testFixImportWithWeakModifier() {
        String content = "syntax = \"proto3\";\nimport weak \"old/path/file.proto\";\nmessage Test {}";
        ProtoContent protoContent = new ProtoContent("test.proto", content);
        protoContent.fixImport("old/path/file.proto", "new/path/file.proto");
        assertEquals("syntax = \"proto3\";\nimport weak \"new/path/file.proto\";\nmessage Test {}", protoContent.getContent());
    }

    @Test
    public void testFixImportWithPublicModifierAndWhitespace() {
        String content = "syntax = \"proto3\";\n  import   public   \"old/path/file.proto\";\nmessage Test {}";
        ProtoContent protoContent = new ProtoContent("test.proto", content);
        protoContent.fixImport("old/path/file.proto", "new/path/file.proto");
        assertEquals("syntax = \"proto3\";\n  import   public   \"new/path/file.proto\";\nmessage Test {}", protoContent.getContent());
    }

    @Test
    public void testFixImportWithWeakModifierAndWhitespace() {
        String content = "syntax = \"proto3\";\n\timport\tweak\t\"old/path/file.proto\";\nmessage Test {}";
        ProtoContent protoContent = new ProtoContent("test.proto", content);
        protoContent.fixImport("old/path/file.proto", "new/path/file.proto");
        assertEquals("syntax = \"proto3\";\n\timport\tweak\t\"new/path/file.proto\";\nmessage Test {}", protoContent.getContent());
    }

    @Test
    public void testFixImportMixedModifiers() {
        String content = "syntax = \"proto3\";\nimport \"old/path/file.proto\";\nimport public \"old/path/file.proto\";\nimport weak \"old/path/file.proto\";\nmessage Test {}";
        ProtoContent protoContent = new ProtoContent("test.proto", content);
        protoContent.fixImport("old/path/file.proto", "new/path/file.proto");
        assertEquals("syntax = \"proto3\";\nimport \"new/path/file.proto\";\nimport public \"new/path/file.proto\";\nimport weak \"new/path/file.proto\";\nmessage Test {}", protoContent.getContent());
    }
}