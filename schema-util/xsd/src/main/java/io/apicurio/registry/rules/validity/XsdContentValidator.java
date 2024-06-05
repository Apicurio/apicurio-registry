package io.apicurio.registry.rules.validity;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rules.RuleViolationException;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.util.SchemaFactoryAccessor;

import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import java.io.InputStream;
import java.util.Map;

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
    public void validate(ValidityLevel level, TypedContent content, Map<String, TypedContent> resolvedReferences) throws RuleViolationException {
        super.validate(level, content, resolvedReferences);

        if (level == ValidityLevel.FULL) {
            try (InputStream semanticStream = content.getContent().stream()) {
                // validate that its a valid schema
                Source source = new StreamSource(semanticStream);
                SchemaFactoryAccessor.getSchemaFactory().newSchema(source);
            } catch (Exception e) {
                throw new RuleViolationException("Syntax violation for XSD Schema artifact.", RuleType.VALIDITY, level.name(), e);
            }
        }
    }
}
