package io.apicurio.registry.rules.validity;

import graphql.schema.idl.SchemaParser;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.rules.RuleViolationException;
import io.apicurio.registry.types.RuleType;

import java.util.List;
import java.util.Map;

/**
 * A content validator implementation for the GraphQL content type.
 */
public class GraphQLContentValidator implements ContentValidator {

    /**
     * Constructor.
     */
    public GraphQLContentValidator() {
    }

    /**
     * @see io.apicurio.registry.rules.validity.ContentValidator#validate(ValidityLevel, TypedContent, Map)
     */
    @Override
    public void validate(ValidityLevel level, TypedContent content,
            Map<String, TypedContent> resolvedReferences) throws RuleViolationException {
        if (level == ValidityLevel.SYNTAX_ONLY || level == ValidityLevel.FULL) {
            try {
                new SchemaParser().parse(content.getContent().content());
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuleViolationException("Syntax violation for GraphQL artifact.", RuleType.VALIDITY,
                        level.name(), e);
            }
        }
    }

    /**
     * @see io.apicurio.registry.rules.validity.ContentValidator#validateReferences(TypedContent, List)
     */
    @Override
    public void validateReferences(TypedContent content, List<ArtifactReference> references)
            throws RuleViolationException {
        // Note: not yet implemented!
    }

}
