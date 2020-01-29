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

package io.apicurio.registry.storage;

import io.apicurio.registry.types.ArtifactState;

/**
 * @author Ales Justin
 */
public class InvalidArtifactStateException extends StorageException {

    private static final long serialVersionUID = 1L;

    public InvalidArtifactStateException(String artifactId, Number version, ArtifactState state) {
        super(String.format("Artifact %s [%s] not active: %s", artifactId, version, state));
    }

    public InvalidArtifactStateException(ArtifactState previousState, ArtifactState newState) {
        super(errorMsg(previousState, newState));
    }

    public static String errorMsg(ArtifactState previousState, ArtifactState newState) {
        return String.format("Cannot transition artifact from %s to %s", previousState, newState);
    }

}
