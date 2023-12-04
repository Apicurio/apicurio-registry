package io.apicurio.registry.rules.validity;

import java.io.InputStream;
import java.util.Map;

import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.rules.RuleViolationException;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.util.SchemaFactoryAccessor;


public class XsdContentValidator extends XmlContentValidator {

    /**
     * Constructor.
     */
    public XsdContentValidator() {
    }
    /**
     * @see io.apicurio.registry.rules.validity.ContentValidator#validate(ValidityLevel, ContentHandle, Map)
     */
    @Override
    public void validate(ValidityLevel level, ContentHandle artifactContent, Map<String, ContentHandle> resolvedReferences) throws RuleViolationException {
        super.validate(level, artifactContent, resolvedReferences);

        if (level == ValidityLevel.FULL) {
            try (InputStream semanticStream = artifactContent.stream()) {
                // validate that its a valid schema
                Source source = new StreamSource(semanticStream);
                SchemaFactoryAccessor.getSchemaFactory().newSchema(source);
            } catch (Exception e) {
                throw new RuleViolationException("Syntax violation for XSD Schema artifact.", RuleType.VALIDITY, level.name(), e);
            }
        }
    }
}
