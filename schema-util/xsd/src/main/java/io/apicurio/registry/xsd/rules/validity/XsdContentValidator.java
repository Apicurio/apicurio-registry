package io.apicurio.registry.xsd.rules.validity;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.xsd.util.MappedReferenceResourceResolver;
import io.apicurio.registry.xsd.util.XsdSchemaCompiler;
import io.apicurio.registry.xml.rules.validity.XmlContentValidator;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;

public class XsdContentValidator extends XmlContentValidator {

    /**
     * Constructor.
     */
    public XsdContentValidator() {
    }

    /**
     * @see io.apicurio.registry.rules.validity.ContentValidator#validate(ValidityLevel, TypedContent, Map)
     */
    @Override
    public void validate(ValidityLevel level, TypedContent content,
            Map<String, TypedContent> resolvedReferences) throws RuleViolationException {
        super.validate(level, content, resolvedReferences);

        if (level == ValidityLevel.FULL) {
            try (InputStream semanticStream = content.getContent().stream()) {
                // validate that its a valid schema, resolving imports only from registry references
                Source source = new StreamSource(semanticStream, MappedReferenceResourceResolver.ROOT_SYSTEM_ID);
                XsdSchemaCompiler.compile(new Source[] { source }, resolvedReferences);
            } catch (XsdSchemaCompiler.UnresolvedReferencesException e) {
                throw XsdSchemaCompiler.toRuleViolation(level, e.getLocations(), "XSD Schema");
            } catch (Exception e) {
                throw new RuleViolationException("Syntax violation for XSD Schema artifact.",
                        RuleType.VALIDITY, level.name(), e);
            }
        }
    }

    /**
     * @see io.apicurio.registry.rules.validity.ContentValidator#validateReferences(TypedContent, List)
     */
    @Override
    public void validateReferences(TypedContent content, List<ArtifactReference> references)
            throws RuleViolationException {
        validateLocatedReferences(content, references);
    }
}
