package io.apicurio.utils.test.raml.microsvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.rules.RuleViolation;
import io.apicurio.registry.rules.RuleViolationException;
import io.apicurio.registry.rules.validity.ContentValidator;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.types.RuleType;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RamlContentValidator implements ContentValidator {

    private static final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    @Override
    public void validate(ValidityLevel level, TypedContent content, Map<String, TypedContent> resolvedReferences) throws RuleViolationException {
        Set<RuleViolation> violations = new HashSet<>();

        if (!content.getContentType().equals("application/x-yaml")) {
            violations.add(new RuleViolation("Incorrect content type.  Expected 'application/x-yaml' but found '" + content.getContentType() + "'.", null));
        } else {
            if (!content.getContent().content().startsWith("#%RAML 1.0")) {
                violations.add(new RuleViolation("Missing '#%RAML 1.0' content header.", null));
            } else {
                try {
                    mapper.readTree(content.getContent().content());
                } catch (Throwable t) {
                    violations.add(new RuleViolation("Failed to parse RAML content.  Expected valid YAML formatted content.", t.getMessage()));
                }
            }
        }

        if (!violations.isEmpty()) {
            throw new RuleViolationException("RAML validation failed.", RuleType.VALIDITY, level.name(), violations);
        }
    }

    @Override
    public void validateReferences(TypedContent content, List<ArtifactReference> references) throws RuleViolationException {
        // TODO implement reference validation
    }
}
