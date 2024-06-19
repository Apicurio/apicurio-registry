package io.apicurio.registry.rules.validity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.apicurio.datamodels.Library;
import io.apicurio.datamodels.TraverserDirection;
import io.apicurio.datamodels.models.Document;
import io.apicurio.datamodels.models.Node;
import io.apicurio.datamodels.models.Referenceable;
import io.apicurio.datamodels.models.visitors.AllNodeVisitor;
import io.apicurio.datamodels.validation.ValidationProblem;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.util.ContentTypeUtil;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.rules.RuleViolation;
import io.apicurio.registry.rules.RuleViolationException;
import io.apicurio.registry.rules.integrity.IntegrityLevel;
import io.apicurio.registry.types.RuleType;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A content validator implementation for the OpenAPI and AsyncAPI content types.
 */
public abstract class ApicurioDataModelContentValidator implements ContentValidator {

    /**
     * @see io.apicurio.registry.rules.validity.ContentValidator#validate(ValidityLevel, TypedContent, Map)
     */
    @Override
    public void validate(ValidityLevel level, TypedContent content,
            Map<String, TypedContent> resolvedReferences) throws RuleViolationException {
        Document document = null;
        if (level == ValidityLevel.SYNTAX_ONLY || level == ValidityLevel.FULL) {
            try {
                JsonNode node = ContentTypeUtil.parseJsonOrYaml(content);
                document = Library.readDocument((ObjectNode) node);
            } catch (Exception e) {
                throw new RuleViolationException("Syntax violation for " + getDataModelType() + " artifact.",
                        RuleType.VALIDITY, level.name(), e);
            }
        }

        if (level == ValidityLevel.FULL) {
            List<ValidationProblem> problems = Library.validate(document, null);
            if (!problems.isEmpty()) {
                Set<RuleViolation> causes = problems.stream()
                        .map(problem -> new RuleViolation(problem.message, problem.nodePath.toString()))
                        .collect(Collectors.toSet());
                throw new RuleViolationException(
                        "The " + getDataModelType() + " artifact is not semantically valid. "
                                + problems.size() + " problems found.",
                        RuleType.VALIDITY, level.name(), causes);
            }
        }
    }

    /**
     * @see io.apicurio.registry.rules.validity.ContentValidator#validateReferences(TypedContent, List)
     */
    @Override
    public void validateReferences(TypedContent content, List<ArtifactReference> references)
            throws RuleViolationException {
        Set<String> mappedRefs = references.stream().map(ref -> ref.getName()).collect(Collectors.toSet());
        Set<String> all$refs = getAll$refs(content);
        Set<RuleViolation> violations = all$refs.stream().filter(ref -> !mappedRefs.contains(ref))
                .map(missingRef -> {
                    return new RuleViolation("Unmapped reference detected.", missingRef);
                }).collect(Collectors.toSet());
        if (!violations.isEmpty()) {
            throw new RuleViolationException("Unmapped reference(s) detected.", RuleType.INTEGRITY,
                    IntegrityLevel.ALL_REFS_MAPPED.name(), violations);
        }
    }

    private Set<String> getAll$refs(TypedContent content) {
        try {
            RefFinder refFinder = new RefFinder();
            JsonNode node = ContentTypeUtil.parseJsonOrYaml(content);
            Document document = Library.readDocument((ObjectNode) node);
            Library.visitTree(document, refFinder, TraverserDirection.down);
            return refFinder.references;
        } catch (Exception e) {
            return Collections.emptySet();
        }
    }

    /**
     * Returns the type of data model being validated. Subclasses must implement.
     */
    protected abstract String getDataModelType();

    private static class RefFinder extends AllNodeVisitor {

        Set<String> references = new HashSet<>();

        /**
         * @see io.apicurio.datamodels.models.visitors.AllNodeVisitor#visitNode(io.apicurio.datamodels.models.Node)
         */
        @Override
        protected void visitNode(Node node) {
            if (node instanceof Referenceable) {
                String theRef = ((Referenceable) node).get$ref();
                if (theRef != null && !theRef.startsWith("#/")) {
                    references.add(theRef);
                }
            }
        }

    }

}
