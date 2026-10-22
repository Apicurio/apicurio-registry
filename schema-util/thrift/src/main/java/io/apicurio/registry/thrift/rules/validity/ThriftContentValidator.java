package io.apicurio.registry.thrift.rules.validity;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.rules.validity.AbstractContentValidator;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.thrift.idl.ThriftIdlParseException;
import io.apicurio.registry.thrift.idl.ThriftIdlParser;
import io.apicurio.registry.types.RuleType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ThriftContentValidator extends AbstractContentValidator {

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
        try {
            ThriftIdlParser.ThriftDocument doc = ThriftIdlParser.parse(content.getContent().content());
            Set<String> includes = new HashSet<>(doc.getIncludes());
            validateMappedReferences(references, includes, "Unmapped Thrift include detected.");
        } catch (RuleViolationException rve) {
            throw rve;
        } catch (Exception e) {
            // Ignore, syntax validation handled in validate method
        }
    }

}
