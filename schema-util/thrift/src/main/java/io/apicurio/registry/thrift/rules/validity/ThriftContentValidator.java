package io.apicurio.registry.thrift.rules.validity;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.rules.validity.ContentValidator;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.thrift.idl.ThriftIdlParseException;
import io.apicurio.registry.thrift.idl.ThriftIdlParser;
import io.apicurio.registry.types.RuleType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class ThriftContentValidator implements ContentValidator {

    private static final Logger log = LoggerFactory.getLogger(ThriftContentValidator.class);

    @Override
    public void validate(ValidityLevel level, TypedContent content,
            Map<String, TypedContent> resolvedReferences) throws RuleViolationException {
        if (level == ValidityLevel.SYNTAX_ONLY || level == ValidityLevel.FULL) {
            try {
                ThriftIdlParser.parse(content.getContent().content());
            } catch (ThriftIdlParseException e) {
                log.debug("Thrift IDL validation failed", e);
                throw new RuleViolationException("Syntax violation for Thrift artifact.", RuleType.VALIDITY,
                        level.name(), e);
            }
        }
    }

    @Override
    public void validateReferences(TypedContent content, List<ArtifactReference> references)
            throws RuleViolationException {
    }

}
