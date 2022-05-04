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

import com.google.common.collect.ImmutableSet;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.rules.compatibility.jsonschema.JsonSchemaDiffLibrary;
import io.apicurio.registry.rules.compatibility.jsonschema.diff.Difference;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * @author Ales Justin
 * @author Jonathan Halliday
 * @author Jakub Senko 'jsenko@redhat.com'
 */
public class JsonSchemaCompatibilityChecker implements CompatibilityChecker {

    @Override
    public CompatibilityExecutionResult testCompatibility(CompatibilityLevel compatibilityLevel, List<ContentHandle> existingArtifacts, ContentHandle proposedArtifact) {
        requireNonNull(compatibilityLevel, "compatibilityLevel MUST NOT be null");
        requireNonNull(existingArtifacts, "existingSchemas MUST NOT be null");
        requireNonNull(proposedArtifact, "proposedSchema MUST NOT be null");

        if (existingArtifacts.isEmpty()) {
            return CompatibilityExecutionResult.compatible();
        }

        final String proposedArtifactContent = proposedArtifact.content();

        Set<Difference> incompatibleDiffs = new HashSet<>();
        String lastExistingSchema = existingArtifacts.get(existingArtifacts.size() - 1).content();

        switch (compatibilityLevel) {
            case BACKWARD:
                incompatibleDiffs = JsonSchemaDiffLibrary.getIncompatibleDifferences(lastExistingSchema, proposedArtifactContent);
                break;
            case BACKWARD_TRANSITIVE:
                incompatibleDiffs = transitively(existingArtifacts, proposedArtifactContent, JsonSchemaDiffLibrary::getIncompatibleDifferences);
                break;
            case FORWARD:
                incompatibleDiffs = JsonSchemaDiffLibrary.getIncompatibleDifferences(proposedArtifactContent, lastExistingSchema);
                break;
            case FORWARD_TRANSITIVE:
                incompatibleDiffs = transitively(existingArtifacts, proposedArtifactContent, (existing, proposed) -> JsonSchemaDiffLibrary.getIncompatibleDifferences(proposed, existing));
                break;
            case FULL:
                incompatibleDiffs = ImmutableSet.<Difference>builder()
                    .addAll(JsonSchemaDiffLibrary.getIncompatibleDifferences(lastExistingSchema, proposedArtifactContent))
                    .addAll(JsonSchemaDiffLibrary.getIncompatibleDifferences(proposedArtifactContent, lastExistingSchema))
                    .build();
                break;
            case FULL_TRANSITIVE:
                incompatibleDiffs = ImmutableSet.<Difference>builder()
                    .addAll(transitively(existingArtifacts, proposedArtifactContent, JsonSchemaDiffLibrary::getIncompatibleDifferences)) // Forward
                    .addAll(transitively(existingArtifacts, proposedArtifactContent, (existing, proposed) -> JsonSchemaDiffLibrary.getIncompatibleDifferences(proposed, existing))) // Backward
                    .build();
                break;
            case NONE:
                break;
        }
        Set<CompatibilityDifference> diffs =
            incompatibleDiffs
                .stream()
                .map(JsonSchemaCompatibilityDifference::new)
                .collect(Collectors.toSet());
        return CompatibilityExecutionResult.incompatible(diffs);
    }

    /**
     * Given a proposed schema, walk the existing schemas in reverse order (i.e. newest to oldest),
     * and for each pair (existing, proposed) call the check function.
     *
     * @return The collected set of differences.
     */
    private Set<Difference> transitively(List<ContentHandle> existingSchemas, String proposedSchema,
                                         BiFunction<String, String, Set<Difference>> checkExistingProposed) {
        Set<Difference> result = new HashSet<>();
        for (int i = existingSchemas.size() - 1; i >= 0; i--) { // TODO This may become too slow, more wide refactoring needed.
            Set<Difference> current = checkExistingProposed.apply(existingSchemas.get(i).content(), proposedSchema);
            result.addAll(current);
        }
        return result;
    }
}
