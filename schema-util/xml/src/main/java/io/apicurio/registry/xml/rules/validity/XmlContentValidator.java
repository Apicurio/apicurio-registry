package io.apicurio.registry.xml.rules.validity;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.rules.validity.AbstractContentValidator;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.xml.refs.XmlReference;
import io.apicurio.registry.xml.refs.XmlReferenceLocator;
import io.apicurio.registry.xml.util.DocumentBuilderAccessor;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class XmlContentValidator extends AbstractContentValidator {

    /**
     * Constructor.
     */
    public XmlContentValidator() {
    }

    /**
     * @see io.apicurio.registry.rules.validity.ContentValidator#validate(ValidityLevel, TypedContent, Map)
     */
    @Override
    public void validate(ValidityLevel level, TypedContent content,
            Map<String, TypedContent> resolvedReferences) throws RuleViolationException {
        if (level == ValidityLevel.SYNTAX_ONLY || level == ValidityLevel.FULL) {
            try (InputStream stream = content.getContent().stream()) {
                DocumentBuilderAccessor.getDocumentBuilder().parse(stream);
            } catch (Exception e) {
                throw new RuleViolationException("Syntax violation for XML artifact.", RuleType.VALIDITY,
                        level.name(), e);
            }
        }
    }

    /**
     * Plain XML artifacts do not support references.
     *
     * @see io.apicurio.registry.rules.validity.ContentValidator#validateReferences(TypedContent, List)
     */
    @Override
    public void validateReferences(TypedContent content, List<ArtifactReference> references)
            throws RuleViolationException {
        // Plain XML has no reference semantics.
    }

    /**
     * Ensures every location found in the content (imports, includes, ...) is mapped by name.
     * Unparseable content is ignored; the VALIDITY rule reports it.
     *
     * @param content the content
     * @param references the mapped references
     * @throws RuleViolationException if a location is not mapped
     */
    protected void validateLocatedReferences(TypedContent content, List<ArtifactReference> references)
            throws RuleViolationException {
        Set<String> required;
        try {
            required = XmlReferenceLocator.locate(content.getContent().content()).stream()
                    .map(XmlReference::location)
                    .collect(Collectors.toSet());
        } catch (XmlReferenceLocator.LocatorException e) {
            return;
        }
        validateMappedReferences(references, required, "Unmapped reference detected.");
    }
}
