package io.apicurio.registry.rules.validity;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.rules.BigqueryGsonBuilder;
import io.apicurio.registry.rules.RuleViolationException;
import io.apicurio.registry.types.RuleType;

import java.util.Map;

public class BigqueryContentValidator extends BigqueryGsonBuilder implements ContentValidator {

    @Override
    public void validate(ValidityLevel level, ContentHandle artifactContent, Map<String, ContentHandle> resolvedReferences)
            throws RuleViolationException {
        try {
            parseFields(artifactContent.content());
        }
        catch (Exception e) {
            throw new RuleViolationException("invalid big query schema", RuleType.VALIDITY, null, e);
        }
    }

}
