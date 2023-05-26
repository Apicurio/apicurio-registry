/*
 * Copyright 2020 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.rules.validity;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.apicurio.datamodels.Library;
import io.apicurio.datamodels.TraverserDirection;
import io.apicurio.datamodels.models.Document;
import io.apicurio.datamodels.models.Node;
import io.apicurio.datamodels.models.Referenceable;
import io.apicurio.datamodels.models.visitors.AllNodeVisitor;
import io.apicurio.datamodels.validation.ValidationProblem;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.rest.v2.beans.ArtifactReference;
import io.apicurio.registry.rules.RuleViolation;
import io.apicurio.registry.rules.RuleViolationException;
import io.apicurio.registry.rules.integrity.IntegrityLevel;
import io.apicurio.registry.types.RuleType;

/**
 * A content validator implementation for the OpenAPI and AsyncAPI content types.
 * @author eric.wittmann@gmail.com
 */
public abstract class ApicurioDataModelContentValidator implements ContentValidator {

    /**
     * @see io.apicurio.registry.rules.validity.ContentValidator#validate(ValidityLevel, ContentHandle, Map)
     */
    @Override
    public void validate(ValidityLevel level, ContentHandle artifactContent, Map<String, ContentHandle> resolvedReferences) throws RuleViolationException {
        Document document = null;
        if (level == ValidityLevel.SYNTAX_ONLY || level == ValidityLevel.FULL) {
            try {
                document = Library.readDocumentFromJSONString(artifactContent.content());
            } catch (Exception e) {
                throw new RuleViolationException("Syntax violation for " + getDataModelType() + " artifact.", RuleType.VALIDITY, level.name(), e);
            }
        }

        if (level == ValidityLevel.FULL) {
            List<ValidationProblem> problems = Library.validate(document, null);
            if (!problems.isEmpty()) {
                Set<RuleViolation> causes = problems.stream().map(problem -> new RuleViolation(problem.message, problem.nodePath.toString())).collect(Collectors.toSet());
                throw new RuleViolationException(
                        "The " + getDataModelType() + " artifact is not semantically valid. " + problems.size() + " problems found.",
                        RuleType.VALIDITY,
                        level.name(),
                        causes);
            }
        }
    }

    /**
     * @see io.apicurio.registry.rules.validity.ContentValidator#validateReferences(io.apicurio.registry.content.ContentHandle, java.util.List)
     */
    @Override
    public void validateReferences(ContentHandle artifactContent, List<ArtifactReference> references) throws RuleViolationException {
        Set<String> mappedRefs = references.stream().map(ref -> ref.getName()).collect(Collectors.toSet());
        Set<String> all$refs = getAll$refs(artifactContent);
        Set<RuleViolation> violations = all$refs.stream().filter(ref -> !mappedRefs.contains(ref)).map(missingRef -> {
            return new RuleViolation("Unmapped reference detected.", missingRef);
        }).collect(Collectors.toSet());
        if (!violations.isEmpty()) {
            throw new RuleViolationException("Unmapped reference(s) detected.", RuleType.INTEGRITY, IntegrityLevel.ALL_REFS_MAPPED.name(), violations);
        }
    }

    private Set<String> getAll$refs(ContentHandle artifactContent) {
        try {
            RefFinder refFinder = new RefFinder();
            Document document = Library.readDocumentFromJSONString(artifactContent.content());
            Library.visitTree(document, refFinder, TraverserDirection.down);
            return refFinder.references;
        } catch (Exception e) {
            return Collections.emptySet();
        }
    }

    /**
     * Returns the type of data model being validated.  Subclasses must implement.
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
