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

import static java.util.Objects.requireNonNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableSet;

import io.apicurio.registry.rules.compatibility.jsonschema.JsonSchemaDiffLibrary;
import io.apicurio.registry.rules.compatibility.jsonschema.diff.Difference;

/**
 * @author Ales Justin
 * @author Jonathan Halliday
 * @author Jakub Senko <jsenko@redhat.com>
 */
public class JsonSchemaCompatibilityChecker implements CompatibilityChecker {

    /**
     * @see CompatibilityChecker#isCompatibleWith(io.apicurio.registry.rules.compatibility.CompatibilityLevel, java.util.List, java.lang.String)
     */
    @Override
    public CompatibilityExecutionResult testCompatibility(CompatibilityLevel compatibilityLevel, List<String> existingSchemas, String proposedSchema) {
        requireNonNull(compatibilityLevel, "compatibilityLevel MUST NOT be null");
        requireNonNull(existingSchemas, "existingSchemas MUST NOT be null");
        requireNonNull(proposedSchema, "proposedSchema MUST NOT be null");

        if (existingSchemas.isEmpty()) {
            return CompatibilityExecutionResult.compatible();
        }
        // TODO There is more information available on which differences are causing an issue. Make it visible to users.
        Set<Difference> incompatibleDiffs = new HashSet<Difference>();
        switch (compatibilityLevel) {
            case BACKWARD:
                incompatibleDiffs = JsonSchemaDiffLibrary.getIncompatibleDifferences(existingSchemas.get(existingSchemas.size() - 1), proposedSchema);
                break;
            case BACKWARD_TRANSITIVE:
                incompatibleDiffs = transitively(existingSchemas, proposedSchema);
                break;
            case FORWARD:
                incompatibleDiffs = JsonSchemaDiffLibrary.getIncompatibleDifferences(proposedSchema, existingSchemas.get(existingSchemas.size() - 1));
                break;
            case FORWARD_TRANSITIVE:
                incompatibleDiffs = transitively(existingSchemas, proposedSchema);
                break;
            case FULL:
                incompatibleDiffs = ImmutableSet.<Difference>builder().addAll(JsonSchemaDiffLibrary.getIncompatibleDifferences(existingSchemas.get(existingSchemas.size() - 1), proposedSchema)).addAll(
                        JsonSchemaDiffLibrary.getIncompatibleDifferences(proposedSchema, existingSchemas.get(existingSchemas.size() - 1))).build();
                break;
            case FULL_TRANSITIVE:
                incompatibleDiffs = ImmutableSet.<Difference>builder().addAll(transitively(existingSchemas, proposedSchema)).addAll(transitively(existingSchemas, proposedSchema)).build();
                break;
            case NONE:
                break;
        }
        Set<CompatibilityDifference> diffs = incompatibleDiffs.stream().map(diff -> (CompatibilityDifference) diff).collect(Collectors.toSet());
        return CompatibilityExecutionResult.incompatible(diffs);
    }

    private Set<Difference> transitively(List<String> existingSchemas, String proposedSchema) {
        String proposed = proposedSchema;
        Set<Difference> resultSet = new HashSet<>();
        for (int i = existingSchemas.size() - 1; i >= 0; i--) { // TODO This may become too slow, more wide refactoring needed.
            String existing = existingSchemas.get(i);
            Set<Difference> currentSet = JsonSchemaDiffLibrary.getIncompatibleDifferences(existing, proposed);
            resultSet.addAll(currentSet);
            proposed = existing;
        }
        return resultSet;
    }
}