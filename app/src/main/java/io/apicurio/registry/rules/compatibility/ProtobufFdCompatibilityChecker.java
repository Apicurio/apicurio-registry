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

import io.apicurio.registry.common.proto.Serde;
import io.apicurio.registry.content.ContentHandle;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * @author Ales Justin
 */
public class ProtobufFdCompatibilityChecker implements CompatibilityChecker {
    @Override
    public CompatibilityExecutionResult testCompatibility(CompatibilityLevel compatibilityLevel, List<ContentHandle> existingArtifacts, ContentHandle proposedArtifact) {
        requireNonNull(compatibilityLevel, "compatibilityLevel MUST NOT be null");
        requireNonNull(existingArtifacts, "existingSchemas MUST NOT be null");
        requireNonNull(proposedArtifact, "proposedSchema MUST NOT be null");
        try {
            Serde.Schema.parseFrom(proposedArtifact.bytes());
            return CompatibilityExecutionResult.compatible();
        } catch (Exception error) {
            return CompatibilityExecutionResult.incompatible(error);
        }
    }

    @Override
    public CompatibilityExecutionResult testCompatibility(CompatibilityLevel compatibilityLevel, List<String> existingSchemas, String proposedSchema) {
        throw new UnsupportedOperationException("String content not supported!"); // should not be called ...
    }
}