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

package io.apicurio.registry.storage;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.slf4j.Logger;

import io.apicurio.registry.types.ArtifactState;

/**
 * @author Ales Justin
 */
@ApplicationScoped
public class ArtifactStateExt {

    private static final Map<ArtifactState, EnumSet<ArtifactState>> transitions;

    static {
        transitions = new HashMap<>();
        transitions.put(ArtifactState.ENABLED, EnumSet.of(ArtifactState.DISABLED, ArtifactState.DEPRECATED));
        transitions.put(ArtifactState.DISABLED, EnumSet.of(ArtifactState.ENABLED, ArtifactState.DEPRECATED));
        transitions.put(ArtifactState.DEPRECATED, EnumSet.of(ArtifactState.ENABLED, ArtifactState.DISABLED));
    }

    public static final EnumSet<ArtifactState> ACTIVE_STATES = EnumSet.of(ArtifactState.ENABLED, ArtifactState.DEPRECATED, ArtifactState.DISABLED);

    @Inject
    Logger log;

    public boolean canTransition(ArtifactState before, ArtifactState after) {
        EnumSet<ArtifactState> states = transitions.get(before);
        return states.contains(after);
    }

    public void validateState(EnumSet<ArtifactState> states, ArtifactState state, String groupId, String artifactId, String version) {
        if (states != null && states.contains(state) == false) {
            throw new InvalidArtifactStateException(groupId, artifactId, version, state);
        }
        logIfDeprecated(groupId, artifactId, version, state);
    }

    public void logIfDeprecated(String groupId, Object artifactId, Object version, ArtifactState state) {
        if (state == ArtifactState.DEPRECATED) {
            log.warn("Artifact {} [{}] in group ({}) is deprecated", artifactId, version, groupId);
        }
    }

    public void applyState(Consumer<ArtifactState> consumer, ArtifactState previousState, ArtifactState newState) {
        if (previousState != null) {
            if (canTransition(previousState, newState)) {
                consumer.accept(newState);
            } else {
                throw new InvalidArtifactStateException(previousState, newState);
            }
        } else {
            consumer.accept(newState);
        }
    }
}
