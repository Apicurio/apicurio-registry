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
import io.apicurio.registry.rules.compatibility.jsonschema.JsonSchemaDiffLibrary;
import io.apicurio.registry.rules.compatibility.jsonschema.diff.Difference;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * @author Ales Justin
 * @author Jonathan Halliday
 * @author Jakub Senko <jsenko@redhat.com>
 */
public class JsonCompatibilityChecker implements CompatibilityChecker { // TODO rename to JsonSchemaCompatibilityChecker

    /**
     * @see CompatibilityChecker#isCompatibleWith(io.apicurio.registry.rules.compatibility.CompatibilityLevel, java.util.List, java.lang.String)
     */
    @Override
    public CompatibilityExecutionResult getIncompatibleDifferences(CompatibilityLevel compatibilityLevel, List<String> existingSchemas, String proposedSchema) {
        requireNonNull(compatibilityLevel, "compatibilityLevel MUST NOT be null");
        requireNonNull(existingSchemas, "existingSchemas MUST NOT be null");
        requireNonNull(proposedSchema, "proposedSchema MUST NOT be null");

        if (existingSchemas.isEmpty()) {
            return new CompatibilityExecutionResult(true, Collections.emptySet());
        }
        // TODO There is more information available on which differences are causing an issue. Make it visible to users.
        Set<Difference> incompatibleDiffs;
        switch (compatibilityLevel) {
            case BACKWARD:
                incompatibleDiffs = JsonSchemaDiffLibrary.getIncompatibleDifferences(existingSchemas.get(existingSchemas.size() - 1), proposedSchema);
                return new CompatibilityExecutionResult(incompatibleDiffs.isEmpty(), incompatibleDiffs);
            case BACKWARD_TRANSITIVE: {
                incompatibleDiffs = transitively(existingSchemas, proposedSchema);
                return new CompatibilityExecutionResult(incompatibleDiffs.isEmpty(), incompatibleDiffs);
            }
            case FORWARD:
                incompatibleDiffs = JsonSchemaDiffLibrary.getIncompatibleDifferences(proposedSchema, existingSchemas.get(existingSchemas.size() - 1));
                return new CompatibilityExecutionResult(incompatibleDiffs.isEmpty(), incompatibleDiffs);
            case FORWARD_TRANSITIVE:
                incompatibleDiffs = transitively(existingSchemas, proposedSchema);
                return new CompatibilityExecutionResult(incompatibleDiffs.isEmpty(), incompatibleDiffs);
            case FULL:
                incompatibleDiffs = ImmutableSet.<Difference>builder().addAll(JsonSchemaDiffLibrary.getIncompatibleDifferences(existingSchemas.get(existingSchemas.size() - 1), proposedSchema)).addAll(
                        JsonSchemaDiffLibrary.getIncompatibleDifferences(proposedSchema, existingSchemas.get(existingSchemas.size() - 1))).build();
                return new CompatibilityExecutionResult(incompatibleDiffs.isEmpty(), incompatibleDiffs);
            case FULL_TRANSITIVE:
                incompatibleDiffs = ImmutableSet.<Difference>builder().addAll(transitively(existingSchemas, proposedSchema)).addAll(transitively(existingSchemas, proposedSchema)).build();
                return new CompatibilityExecutionResult(incompatibleDiffs.isEmpty(), incompatibleDiffs);
            default:
                return new CompatibilityExecutionResult(true, Collections.emptySet());
        }
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
