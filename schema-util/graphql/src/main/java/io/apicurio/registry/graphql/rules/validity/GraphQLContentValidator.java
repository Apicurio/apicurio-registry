package io.apicurio.registry.graphql.rules.validity;

import graphql.schema.idl.SchemaParser;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.rules.validity.ContentValidator;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.types.RuleType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * A content validator implementation for the GraphQL content type.
 */
public class GraphQLContentValidator implements ContentValidator {

    private static final Logger log = LoggerFactory.getLogger(GraphQLContentValidator.class);

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
                log.debug("GraphQL schema validation failed", e);
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
