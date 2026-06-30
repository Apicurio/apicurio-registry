package io.apicurio.registry.rules.validity;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.thrift.idl.ThriftIdlParser;
import io.apicurio.registry.thrift.idl.ThriftIdlParser.ThriftDocument;
import io.apicurio.registry.thrift.rules.validity.ThriftContentValidator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;

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

        Assertions.assertEquals(2, doc.getNamespaces().size());
        Assertions.assertEquals(1, doc.getTypedefs().size());
        Assertions.assertEquals(2, doc.getConstants().size());
        Assertions.assertTrue(doc.getConstants().stream()
                .anyMatch(c -> "MAPCONSTANT".equals(c.getName())
                        && "map<string, string>".equals(c.getType())));
        Assertions.assertEquals(1, doc.getEnums().size());
        Assertions.assertEquals(1, doc.getStructs().size());
        Assertions.assertEquals(1, doc.getExceptions().size());
        Assertions.assertEquals(1, doc.getServices().size());
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
