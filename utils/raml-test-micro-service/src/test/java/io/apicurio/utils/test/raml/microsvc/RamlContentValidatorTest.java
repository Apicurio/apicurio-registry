package io.apicurio.utils.test.raml.microsvc;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.types.ContentTypes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

/**
 * Tests the {@link RamlContentValidator}, specifically its reference validation behavior.
 */
public class RamlContentValidatorTest {

    private static final String RAML_WITH_INCLUDE = "#%RAML 1.0\n"
            + "title: Test API\n"
            + "types:\n"
            + "  Widget: ~include widget.raml\n";

    private static final String RAML_WITHOUT_INCLUDE = "#%RAML 1.0\n"
            + "title: Test API\n"
            + "types:\n"
            + "  Widget:\n"
            + "    type: object\n";

    @Test
    public void testValidateReferencesWithMappedInclude() {
        TypedContent content = TypedContent.create(ContentHandle.create(RAML_WITH_INCLUDE),
                ContentTypes.APPLICATION_YAML);
        RamlContentValidator validator = new RamlContentValidator();
        List<ArtifactReference> references = List.of(ArtifactReference.builder().groupId("default")
                .artifactId("Widget").version("1.0").name("widget.raml").build());

        Assertions.assertDoesNotThrow(() -> validator.validateReferences(content, references));
    }

    @Test
    public void testValidateReferencesWithUnmappedInclude() {
        TypedContent content = TypedContent.create(ContentHandle.create(RAML_WITH_INCLUDE),
                ContentTypes.APPLICATION_YAML);
        RamlContentValidator validator = new RamlContentValidator();

        RuleViolationException error = Assertions.assertThrows(RuleViolationException.class,
                () -> validator.validateReferences(content, Collections.emptyList()));
        Assertions.assertEquals("widget.raml", error.getCauses().iterator().next().getContext());
    }

    @Test
    public void testValidateReferencesWithNoIncludes() {
        TypedContent content = TypedContent.create(ContentHandle.create(RAML_WITHOUT_INCLUDE),
                ContentTypes.APPLICATION_YAML);
        RamlContentValidator validator = new RamlContentValidator();

        Assertions.assertDoesNotThrow(() -> validator.validateReferences(content, null));
    }
}
