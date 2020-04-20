/*
 * Copyright 2019 Red Hat
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

import io.apicurio.registry.rules.compatibility.jsonschema.JsonSchemaDiffLibrary;

import java.util.List;
import java.util.function.BiPredicate;

import static io.apicurio.registry.rules.compatibility.jsonschema.JsonSchemaDiffLibrary.isCompatible;
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
    public boolean isCompatibleWith(CompatibilityLevel compatibilityLevel, List<String> existingSchemas, String proposedSchema) {
        requireNonNull(compatibilityLevel, "compatibilityLevel MUST NOT be null");
        requireNonNull(existingSchemas, "existingSchemas MUST NOT be null");
        requireNonNull(proposedSchema, "proposedSchema MUST NOT be null");

        if (existingSchemas.isEmpty()) {
            return true;
        }
        // TODO There is more information available on which differences are causing an issue. Make it visible to users.
        switch (compatibilityLevel) {
            case BACKWARD:
                return isCompatible(existingSchemas.get(existingSchemas.size() - 1), proposedSchema);
            case BACKWARD_TRANSITIVE: {
                return transitively(existingSchemas, proposedSchema, JsonSchemaDiffLibrary::isCompatible);
            }
            case FORWARD:
                return isCompatible(proposedSchema, existingSchemas.get(existingSchemas.size() - 1));
            case FORWARD_TRANSITIVE:
                return transitively(existingSchemas, proposedSchema, (e, p) -> isCompatible(p, e));
            case FULL:
                return isCompatible(existingSchemas.get(existingSchemas.size() - 1), proposedSchema) &&
                    isCompatible(proposedSchema, existingSchemas.get(existingSchemas.size() - 1));
            case FULL_TRANSITIVE:
                return transitively(existingSchemas, proposedSchema, JsonSchemaDiffLibrary::isCompatible) &&
                    transitively(existingSchemas, proposedSchema, (e, p) -> isCompatible(p, e));
            default:
                return true;
        }
    }

    private boolean transitively(List<String> existingSchemas, String proposedSchema,
                                 BiPredicate<String, String> checkProposedExisting) {
        String proposed = proposedSchema;
        for (int i = existingSchemas.size() - 1; i >= 0; i--) { // TODO This may become too slow, more wide refactoring needed.
            String existing = existingSchemas.get(i);
            boolean result = checkProposedExisting.test(existing, proposed);
            proposed = existing;
            if (!result) {
                return false;
            }
        }
        return true;
    }
}
