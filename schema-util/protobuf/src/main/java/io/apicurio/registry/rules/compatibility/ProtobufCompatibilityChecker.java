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

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.rules.compatibility.protobuf.ProtobufCompatibilityCheckerLibrary;
import io.apicurio.registry.utils.protobuf.schema.ProtobufFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author Ales Justin
 */
public class ProtobufCompatibilityChecker implements CompatibilityChecker {

    @Override
    public CompatibilityExecutionResult testCompatibility(CompatibilityLevel compatibilityLevel, List<ContentHandle> existingArtifacts, ContentHandle proposedArtifact) {
        requireNonNull(compatibilityLevel, "compatibilityLevel MUST NOT be null");
        requireNonNull(existingArtifacts, "existingArtifacts MUST NOT be null");
        requireNonNull(proposedArtifact, "proposedArtifact MUST NOT be null");

        if (existingArtifacts.isEmpty()) {
            return CompatibilityExecutionResult.compatible();
        }

        ProtobufFile fileBefore = new ProtobufFile(existingArtifacts.get(existingArtifacts.size() - 1).content());
        ProtobufFile fileAfter = new ProtobufFile(proposedArtifact.content());

        switch (compatibilityLevel) {
            case BACKWARD: {
                return testBackward(fileBefore, fileAfter);
            }
            case BACKWARD_TRANSITIVE: {
                return testBackwardTransitive(existingArtifacts, fileAfter);
            }
            case FORWARD: {
                return testForward(fileBefore, fileAfter);
            }
            case FORWARD_TRANSITIVE: {
                return testForwardTransitive(existingArtifacts, fileAfter);
            }
            case FULL: {
                return testFull(fileBefore, fileAfter);
            }
            case FULL_TRANSITIVE: {
                return testFullTransitive(existingArtifacts, fileAfter);
            }
            default:
                return CompatibilityExecutionResult.compatible();
        }
    }

    @NotNull
    private CompatibilityExecutionResult testFullTransitive(List<ContentHandle> existingSchemas, ProtobufFile fileAfter) {
        ProtobufFile fileBefore;
        for (ContentHandle existing : existingSchemas) {
            fileBefore = new ProtobufFile(existing.content());
            if (!testFull(fileBefore, fileAfter).isCompatible()) {
                return CompatibilityExecutionResult.incompatible("The new version of the protobuf artifact is not fully compatible.");
            }
        }
        return CompatibilityExecutionResult.compatible();
    }

    @NotNull
    private CompatibilityExecutionResult testFull(ProtobufFile fileBefore, ProtobufFile fileAfter) {
        ProtobufCompatibilityCheckerLibrary backwardChecker = new ProtobufCompatibilityCheckerLibrary(fileBefore, fileAfter);
        ProtobufCompatibilityCheckerLibrary forwardChecker = new ProtobufCompatibilityCheckerLibrary(fileAfter, fileBefore);
        if (!backwardChecker.validate() && !forwardChecker.validate()) {
            return CompatibilityExecutionResult.incompatible("The new version of the protobuf artifact is not fully compatible.");
        } else {
            return CompatibilityExecutionResult.compatible();
        }
    }

    @NotNull
    private CompatibilityExecutionResult testForwardTransitive(List<ContentHandle> existingSchemas, ProtobufFile fileAfter) {
        ProtobufFile fileBefore;
        for (ContentHandle existing : existingSchemas) {
            fileBefore = new ProtobufFile(existing.content());
            ProtobufCompatibilityCheckerLibrary checker = new ProtobufCompatibilityCheckerLibrary(fileAfter, fileBefore);
            if (!checker.validate()) {
                return CompatibilityExecutionResult.incompatible("The new version of the protobuf artifact is not forward compatible.");
            }
        }
        return CompatibilityExecutionResult.compatible();
    }

    @NotNull
    private CompatibilityExecutionResult testForward(ProtobufFile fileBefore, ProtobufFile fileAfter) {
        ProtobufCompatibilityCheckerLibrary checker = new ProtobufCompatibilityCheckerLibrary(fileAfter, fileBefore);
        if (checker.validate()) {
            return CompatibilityExecutionResult.compatible();
        } else {
            return CompatibilityExecutionResult.incompatible("The new version of the protobuf artifact is not forward compatible.");
        }
    }

    @NotNull
    private CompatibilityExecutionResult testBackwardTransitive(List<ContentHandle> existingSchemas, ProtobufFile fileAfter) {
        ProtobufFile fileBefore;
        for (ContentHandle existing : existingSchemas) {
            fileBefore = new ProtobufFile(existing.content());
            ProtobufCompatibilityCheckerLibrary checker = new ProtobufCompatibilityCheckerLibrary(fileBefore, fileAfter);
            if (!checker.validate()) {
                return CompatibilityExecutionResult.incompatible("The new version of the protobuf artifact is not backward compatible.");
            }
        }
        return CompatibilityExecutionResult.compatible();
    }

    @NotNull
    private CompatibilityExecutionResult testBackward(ProtobufFile fileBefore, ProtobufFile fileAfter) {
        ProtobufCompatibilityCheckerLibrary checker = new ProtobufCompatibilityCheckerLibrary(fileBefore, fileAfter);
        if (checker.validate()) {
            return CompatibilityExecutionResult.compatible();
        } else {
            return CompatibilityExecutionResult.incompatible("The new version of the protobuf artifact is not backward compatible.");
        }
    }
}