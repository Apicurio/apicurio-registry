package io.apicurio.registry.rules.validity;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.rules.RuleViolationException;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.util.DocumentBuilderAccessor;

public class XmlContentValidator implements ContentValidator {

    /**
     * Constructor.
     */
    public XmlContentValidator() {
    }

    /**
     * @see io.apicurio.registry.rules.validity.ContentValidator#validate(ValidityLevel, ContentHandle, java.util.Map)
     */
    @Override
    public void validate(ValidityLevel level, ContentHandle artifactContent, Map<String, ContentHandle> resolvedReferences) throws RuleViolationException {
        if (level == ValidityLevel.SYNTAX_ONLY || level == ValidityLevel.FULL) {
            try (InputStream stream = artifactContent.stream()) {
                DocumentBuilderAccessor.getDocumentBuilder().parse(stream);
            } catch (Exception e) {
                throw new RuleViolationException("Syntax violation for XML artifact.", RuleType.VALIDITY, level.name(), e);
            }
        }
    }

    /**
     * @see io.apicurio.registry.rules.validity.ContentValidator#validateReferences(io.apicurio.registry.content.ContentHandle, java.util.List)
     */
    @Override
    public void validateReferences(ContentHandle artifactContent, List<ArtifactReference> references) throws RuleViolationException {
        // Note: not yet implemented!
    }

}
