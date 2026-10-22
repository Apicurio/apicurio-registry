package io.apicurio.registry.rules.validity;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.thrift.idl.ThriftIdlParser;
import io.apicurio.registry.thrift.idl.ThriftIdlParser.ThriftDocument;
import io.apicurio.registry.thrift.rules.validity.ThriftContentValidator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

public class ThriftContentValidatorTest extends ArtifactUtilProviderTestBase {

    @Test
    public void testValidSyntax() throws Exception {
        TypedContent content = resourceToTypedContentHandle("thrift-valid.thrift");
        ThriftContentValidator validator = new ThriftContentValidator();
        validator.validate(ValidityLevel.SYNTAX_ONLY, content, Collections.emptyMap());
    }

    @Test
    public void testValidContentIsFullyParsed() throws Exception {
        TypedContent content = resourceToTypedContentHandle("thrift-valid.thrift");
        ThriftDocument doc = ThriftIdlParser.parse(content.getContent().content());

        List<String> namespaces = doc.getNamespaces().stream()
                .map(n -> n.getLanguage() + " " + n.getName()).toList();
        Assertions.assertEquals(List.of("java com.example.tutorial", "py tutorial"), namespaces);

        List<String> typedefs = doc.getTypedefs().stream()
                .map(t -> t.getName() + ":" + t.getType()).toList();
        Assertions.assertEquals(List.of("MyInteger:i32"), typedefs);

        List<String> constants = doc.getConstants().stream()
                .map(c -> c.getName() + ":" + c.getType()).toList();
        Assertions.assertEquals(
                List.of("INT32CONSTANT:i32", "MAPCONSTANT:map<string, string>"), constants);

        Assertions.assertEquals(List.of("Operation"), doc.getEnums());
        Assertions.assertEquals(List.of("Work"), doc.getStructs());
        Assertions.assertEquals(List.of("InvalidOperation"), doc.getExceptions());
        Assertions.assertEquals(List.of("Calculator"), doc.getServices());
    }

    @Test
    public void testValidFull() throws Exception {
        TypedContent content = resourceToTypedContentHandle("thrift-valid.thrift");
        ThriftContentValidator validator = new ThriftContentValidator();
        validator.validate(ValidityLevel.FULL, content, Collections.emptyMap());
    }

    @Test
    public void testInvalidSyntax() throws Exception {
        TypedContent content = resourceToTypedContentHandle("thrift-invalid.thrift");
        ThriftContentValidator validator = new ThriftContentValidator();
        Assertions.assertThrows(RuleViolationException.class, () -> {
            validator.validate(ValidityLevel.SYNTAX_ONLY, content, Collections.emptyMap());
        });
    }

    @Test
    public void testNoneLevel() throws Exception {
        TypedContent content = resourceToTypedContentHandle("thrift-invalid.thrift");
        ThriftContentValidator validator = new ThriftContentValidator();
        // NONE level should not throw even for invalid content
        validator.validate(ValidityLevel.NONE, content, Collections.emptyMap());
    }

}
