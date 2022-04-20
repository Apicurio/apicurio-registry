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

import io.apicurio.registry.rules.compatibility.protobuf.ProtobufCompatibilityCheckerLibrary;
import io.apicurio.registry.utils.protobuf.schema.ProtobufFile;

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

        ProtobufFile fileBefore = new ProtobufFile(existingSchemas.get(existingSchemas.size() - 1));
        ProtobufFile fileAfter = new ProtobufFile(proposedSchema);

        switch (compatibilityLevel) {
            case BACKWARD: {
                ProtobufCompatibilityCheckerLibrary checker = new ProtobufCompatibilityCheckerLibrary(fileBefore, fileAfter);
                if (checker.validate()) {
                    return CompatibilityExecutionResult.compatible();
                } else {
                    return CompatibilityExecutionResult.incompatible("The new version of the protobuf artifact is not backward compatible.");
                }
            }
            case BACKWARD_TRANSITIVE: {
                for (String existing : existingSchemas) {
                    fileBefore = new ProtobufFile(existing);
                    ProtobufCompatibilityCheckerLibrary checker = new ProtobufCompatibilityCheckerLibrary(fileBefore, fileAfter);
                    if (!checker.validate()) {
                        return CompatibilityExecutionResult.incompatible("The new version of the protobuf artifact is not backward compatible.");
                    }
                }
                return CompatibilityExecutionResult.compatible();
            }
            case FORWARD: {
                ProtobufCompatibilityCheckerLibrary checker = new ProtobufCompatibilityCheckerLibrary(fileAfter, fileBefore);
                if (checker.validate()) {
                    return CompatibilityExecutionResult.compatible();
                } else {
                    return CompatibilityExecutionResult.incompatible("The new version of the protobuf artifact is not forward compatible.");
                }
            }
            case FORWARD_TRANSITIVE: {
                for (String existing : existingSchemas) {
                    fileBefore = new ProtobufFile(existing);
                    ProtobufCompatibilityCheckerLibrary checker = new ProtobufCompatibilityCheckerLibrary(fileAfter, fileBefore);
                    if (!checker.validate()) {
                        return CompatibilityExecutionResult.incompatible("The new version of the protobuf artifact is not forward compatible.");
                    }
                }
                return CompatibilityExecutionResult.compatible();
            }
            case FULL: {
                ProtobufCompatibilityCheckerLibrary backwardChecker = new ProtobufCompatibilityCheckerLibrary(fileBefore, fileAfter);
                ProtobufCompatibilityCheckerLibrary forwardChecker = new ProtobufCompatibilityCheckerLibrary(fileAfter, fileBefore);
                if (backwardChecker.validate() && forwardChecker.validate()) {
                    return CompatibilityExecutionResult.compatible();
                } else {
                    return CompatibilityExecutionResult.incompatible("The new version of the protobuf artifact is not fully compatible.");
                }
            }
            case FULL_TRANSITIVE:
                for (String existing : existingSchemas) {
                    fileBefore = new ProtobufFile(existing);
                    ProtobufCompatibilityCheckerLibrary backwardChecker = new ProtobufCompatibilityCheckerLibrary(fileBefore, fileAfter);
                    ProtobufCompatibilityCheckerLibrary forwardChecker = new ProtobufCompatibilityCheckerLibrary(fileAfter, fileBefore);
                    if (!backwardChecker.validate() || !forwardChecker.validate()) {
                        return CompatibilityExecutionResult.incompatible("The new version of the protobuf artifact is not fully compatible.");
                    }
                }
                throw new IllegalStateException("Compatibility level " + compatibilityLevel + " not supported for Protobuf schemas");
            default:
                return CompatibilityExecutionResult.compatible();
        }
    }
}