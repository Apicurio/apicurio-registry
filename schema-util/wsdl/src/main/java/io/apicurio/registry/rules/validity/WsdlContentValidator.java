package io.apicurio.registry.rules.validity;

import java.io.InputStream;
import java.util.Map;

import org.w3c.dom.Document;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.rules.RuleViolationException;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.util.DocumentBuilderAccessor;
import io.apicurio.registry.util.WSDLReaderAccessor;

public class WsdlContentValidator extends XmlContentValidator {

    /**
     * Constructor.
     */
    public WsdlContentValidator() {
    }

    /**
     * @see io.apicurio.registry.rules.validity.ContentValidator#validate(ValidityLevel, ContentHandle, Map)
     */
    @Override
    public void validate(ValidityLevel level, ContentHandle artifactContent, Map<String, ContentHandle> resolvedReferences) throws RuleViolationException {
        if (level == ValidityLevel.SYNTAX_ONLY || level == ValidityLevel.FULL) {
            try (InputStream stream = artifactContent.stream()) {
                Document wsdlDoc = DocumentBuilderAccessor.getDocumentBuilder().parse(stream);
                if (level == ValidityLevel.FULL) {
                    // validate that its a valid schema
                    WSDLReaderAccessor.getWSDLReader().readWSDL(null, wsdlDoc);
                }
            } catch (Exception e) {
                throw new RuleViolationException("Syntax violation for WSDL Schema artifact.", RuleType.VALIDITY, level.name(), e);
            }
        }
    }
}
