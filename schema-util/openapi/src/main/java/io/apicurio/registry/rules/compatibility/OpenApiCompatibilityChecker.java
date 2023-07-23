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

package io.apicurio.registry.rules.compatibility;

import com.agoda.common.openapi.schema.verification.AgOpenApiSchemaComparator;
import com.agoda.common.openapi.schema.verification.CompatibilityMode;
import com.agoda.common.openapi.schema.verification.CompatibilityResponse;
import io.apicurio.registry.content.ContentHandle;
//import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * @author Sittikun R.
 */
public class OpenApiCompatibilityChecker implements CompatibilityChecker {

    @Override
    public CompatibilityExecutionResult testCompatibility(CompatibilityLevel compatibilityLevel, List<ContentHandle> existingArtifacts, ContentHandle proposedArtifact, Map<String, ContentHandle> resolvedReferences) {
        requireNonNull(compatibilityLevel, "compatibilityLevel MUST NOT be null");
        requireNonNull(existingArtifacts, "existingArtifacts MUST NOT be null");
        requireNonNull(proposedArtifact, "proposedArtifact MUST NOT be null");

        if (existingArtifacts.isEmpty()) {
            return CompatibilityExecutionResult.compatible();
        }

        String stringBefore = existingArtifacts.get(existingArtifacts.size() - 1).content();
        String stringAfter = proposedArtifact.content();

        switch (compatibilityLevel) {
            case BACKWARD: {
                return testBackward(stringBefore, stringAfter);
            }
            case BACKWARD_TRANSITIVE: {
                return testBackwardTransitive(existingArtifacts, stringAfter);
            }
            case FORWARD: {
                return testForward(stringBefore, stringAfter);
            }
            case FORWARD_TRANSITIVE: {
                return testForwardTransitive(existingArtifacts, stringAfter);
            }
            case FULL: {
                return testFull(stringBefore, stringAfter);
            }
            case FULL_TRANSITIVE: {
                return testFullTransitive(existingArtifacts, stringAfter);
            }
            default:
                return CompatibilityExecutionResult.compatible();
        }
    }

    //@NotNull
    private CompatibilityExecutionResult testFullTransitive(List<ContentHandle> existingSchemas, String fileAfter) {
        for (ContentHandle existing : existingSchemas) {
            AgOpenApiSchemaComparator comparator = new AgOpenApiSchemaComparator();
            CompatibilityResponse compatibilityResponse = comparator.compare(existing.content(), fileAfter, CompatibilityMode.FULL);
            Set<CompatibilityDifference> compatibilityDifferenceSet = compatibilityResponse.getErrorMessages().stream().map(errorMessage -> new SimpleCompatibilityDifference(errorMessage)).collect(Collectors.toSet());
            CompatibilityExecutionResult result = CompatibilityExecutionResult.incompatibleOrEmpty(compatibilityDifferenceSet);
            return result;
        }
        return CompatibilityExecutionResult.compatible();
    }

    //@NotNull
    private CompatibilityExecutionResult testFull(String fileBefore, String fileAfter) {
        AgOpenApiSchemaComparator comparator = new AgOpenApiSchemaComparator();
        CompatibilityResponse compatibilityResponse = comparator.compare(fileBefore, fileAfter, CompatibilityMode.FULL);
        Set<CompatibilityDifference> compatibilityDifferenceSet = compatibilityResponse.getErrorMessages().stream().map(errorMessage -> new SimpleCompatibilityDifference(errorMessage)).collect(Collectors.toSet());
        CompatibilityExecutionResult result = CompatibilityExecutionResult.incompatibleOrEmpty(compatibilityDifferenceSet);
        return result;
    }

    //@NotNull
    private CompatibilityExecutionResult testForwardTransitive(List<ContentHandle> existingSchemas, String fileAfter) {
        for (ContentHandle existing : existingSchemas) {
            AgOpenApiSchemaComparator comparator = new AgOpenApiSchemaComparator();
            CompatibilityResponse compatibilityResponse = comparator.compare(existing.content(), fileAfter, CompatibilityMode.FORWARD);
            Set<CompatibilityDifference> compatibilityDifferenceSet = compatibilityResponse.getErrorMessages().stream().map(errorMessage -> new SimpleCompatibilityDifference(errorMessage)).collect(Collectors.toSet());
            CompatibilityExecutionResult result = CompatibilityExecutionResult.incompatibleOrEmpty(compatibilityDifferenceSet);
            return result;
        }
        return CompatibilityExecutionResult.compatible();
    }

    //@NotNull
    private CompatibilityExecutionResult testForward(String fileBefore, String fileAfter) {
        AgOpenApiSchemaComparator comparator = new AgOpenApiSchemaComparator();
        CompatibilityResponse compatibilityResponse = comparator.compare(fileBefore, fileAfter, CompatibilityMode.FORWARD);
        Set<CompatibilityDifference> compatibilityDifferenceSet = compatibilityResponse.getErrorMessages().stream().map(errorMessage -> new SimpleCompatibilityDifference(errorMessage)).collect(Collectors.toSet());
        CompatibilityExecutionResult result = CompatibilityExecutionResult.incompatibleOrEmpty(compatibilityDifferenceSet);
        return result;
    }

    //@NotNull
    private CompatibilityExecutionResult testBackwardTransitive(List<ContentHandle> existingSchemas, String fileAfter) {
        for (ContentHandle existing : existingSchemas) {
            AgOpenApiSchemaComparator comparator = new AgOpenApiSchemaComparator();
            CompatibilityResponse compatibilityResponse = comparator.compare(existing.content(), fileAfter, CompatibilityMode.BACKWARD);
            Set<CompatibilityDifference> compatibilityDifferenceSet = compatibilityResponse.getErrorMessages().stream().map(errorMessage -> new SimpleCompatibilityDifference(errorMessage)).collect(Collectors.toSet());
            CompatibilityExecutionResult result = CompatibilityExecutionResult.incompatibleOrEmpty(compatibilityDifferenceSet);
            return result;
        }
        return CompatibilityExecutionResult.compatible();
    }

    //@NotNull
    private CompatibilityExecutionResult testBackward(String fileBefore, String fileAfter) {
        AgOpenApiSchemaComparator comparator = new AgOpenApiSchemaComparator();
        CompatibilityResponse compatibilityResponse = comparator.compare(fileBefore, fileAfter, CompatibilityMode.BACKWARD);
        Set<CompatibilityDifference> compatibilityDifferenceSet = compatibilityResponse.getErrorMessages().stream().map(errorMessage -> new SimpleCompatibilityDifference(errorMessage)).collect(Collectors.toSet());
        CompatibilityExecutionResult result = CompatibilityExecutionResult.incompatibleOrEmpty(compatibilityDifferenceSet);
        return result;
    }
}