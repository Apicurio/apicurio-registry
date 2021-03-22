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

import java.util.List;

/**
 * @author Ales Justin
 */
public class ProtobufCompatibilityChecker implements CompatibilityChecker {

    /**
     * @see io.apicurio.registry.rules.compatibility.CompatibilityChecker#testCompatibility(io.apicurio.registry.rules.compatibility.CompatibilityLevel, java.util.List, java.lang.String)
     */
    @Override
    public CompatibilityExecutionResult testCompatibility(CompatibilityLevel compatibilityLevel, List<String> existingSchemas, String proposedSchema) {
        requireNonNull(compatibilityLevel, "compatibilityLevel MUST NOT be null");
        requireNonNull(existingSchemas, "existingSchemas MUST NOT be null");
        requireNonNull(proposedSchema, "proposedSchema MUST NOT be null");

        if (existingSchemas.isEmpty()) {
            return CompatibilityExecutionResult.compatible();
        }
        switch (compatibilityLevel) {
            case BACKWARD:
            case FORWARD:
            case FULL: {
                return checkCompatibilityToPreviousVersion(compatibilityLevel, existingSchemas, proposedSchema);
            }
            case BACKWARD_TRANSITIVE:
            case FORWARD_TRANSITIVE:
            case FULL_TRANSITIVE: {
                return checkCompatibilityTransitive(compatibilityLevel, existingSchemas, proposedSchema);
            }
            default:
                return CompatibilityExecutionResult.compatible();
        }
    }

    private CompatibilityExecutionResult checkCompatibilityToPreviousVersion(CompatibilityLevel compatibilityLevel, List<String> existingSchemas, String proposedSchema) {
        ProtobufFile fileBefore = new ProtobufFile(existingSchemas.get(existingSchemas.size() - 1));
        ProtobufFile fileAfter = new ProtobufFile(proposedSchema);
        ProtobufCompatibilityCheckerImpl checker = new ProtobufCompatibilityCheckerImpl(fileBefore, fileAfter);
        if (checker.validate()) {
            return CompatibilityExecutionResult.compatible();
        } else {
            return CompatibilityExecutionResult.incompatible(String.format("The new version of the protobuf artifact is not %s compatible.", compatibilityLevel.toString()));
        }
    }

    private CompatibilityExecutionResult checkCompatibilityTransitive(CompatibilityLevel compatibilityLevel, List<String> existingSchemas, String proposedSchema) {
        ProtobufFile fileAfter = new ProtobufFile(proposedSchema);
        for (String existing : existingSchemas) {
            ProtobufFile fileBefore = new ProtobufFile(existing);
            ProtobufCompatibilityCheckerImpl checker = new ProtobufCompatibilityCheckerImpl(fileBefore, fileAfter);
            if (checker.validate()) {
                return CompatibilityExecutionResult.compatible();
            } else {
                return CompatibilityExecutionResult.incompatible(String.format("The new version of the protobuf artifact is not %s compatible.", compatibilityLevel.toString()));
            }
        }
        return CompatibilityExecutionResult.compatible();
    }
}