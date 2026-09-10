package io.apicurio.registry.examples.customtypes;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.rules.validity.ContentValidator;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.rules.violation.RuleViolation;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.types.RuleType;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Backs the VALIDITY rule for the MARKDOWN artifact type.
 * <ul>
 * <li>{@code SYNTAX_ONLY}: the document must not be blank.</li>
 * <li>{@code FULL}: additionally, the document must start with a level-1 heading (its title).</li>
 * </ul>
 */
public class MarkdownContentValidator implements ContentValidator {

    @Override
    public void validate(ValidityLevel level, TypedContent content, Map<String, TypedContent> resolvedReferences)
            throws RuleViolationException {
        if (level == null || level == ValidityLevel.NONE) {
            return;
        }
        String markdown = Markdown.text(content);
        Set<RuleViolation> violations = new LinkedHashSet<>();
        if (markdown == null || markdown.isBlank()) {
            violations.add(new RuleViolation("Markdown document is empty.", null));
        } else if (level == ValidityLevel.FULL && Markdown.title(markdown) == null) {
            violations.add(new RuleViolation("Markdown document must start with a level-1 heading (\"# Title\").",
                    "line 1"));
        }
        if (!violations.isEmpty()) {
            throw new RuleViolationException("Markdown validation failed.", RuleType.VALIDITY, level.name(),
                    violations);
        }
    }

    @Override
    public void validateReferences(TypedContent content, List<ArtifactReference> references)
            throws RuleViolationException {
        // Markdown documents do not reference other registry artifacts.
    }
}
