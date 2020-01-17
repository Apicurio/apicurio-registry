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

import io.apicurio.registry.types.ArtifactState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.apicurio.registry.storage.MetaDataKeys.STATE;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * @author Ales Justin
 */
public class ArtifactStateExt {
    private static final Logger log = LoggerFactory.getLogger(ArtifactStateExt.class);

    private static final Map<ArtifactState, EnumSet<ArtifactState>> transitions;

    static {
        transitions = new HashMap<>();
        transitions.put(ArtifactState.ENABLED, EnumSet.of(ArtifactState.DISABLED, ArtifactState.DEPRECATED, ArtifactState.DELETED));
        transitions.put(ArtifactState.DISABLED, EnumSet.of(ArtifactState.ENABLED, ArtifactState.DEPRECATED, ArtifactState.DELETED));
        transitions.put(ArtifactState.DEPRECATED, EnumSet.of(ArtifactState.DELETED));
        transitions.put(ArtifactState.DELETED, EnumSet.noneOf(ArtifactState.class));
    }

    public static final EnumSet<ArtifactState> ACTIVE_STATES = EnumSet.of(ArtifactState.ENABLED, ArtifactState.DEPRECATED);
    public static final EnumSet<ArtifactState> ALL = EnumSet.allOf(ArtifactState.class);

    public static boolean canTransition(ArtifactState before, ArtifactState after) {
        EnumSet<ArtifactState> states = transitions.get(before);
        return states.contains(after);
    }

    private static String getStateRaw(Map<String, String> context) {
        return context.get(STATE);
    }

    public static ArtifactState getState(Map<String, String> context) {
        return ArtifactState.valueOf(getStateRaw(context));
    }

    public static void validateState(EnumSet<ArtifactState> states, ArtifactState state, String identifier, Number version) {
        if (states.contains(state) == false) {
            throw new InvalidArtifactStateException(identifier, version, state);
        }
        ArtifactStateExt.logIfDeprecated(identifier, state, version);
    }

    public static void logIfDeprecated(Object identifier, ArtifactState state, Object version) {
        if (state == ArtifactState.DEPRECATED) {
            log.warn("Artifact {} [{}] is deprecated", identifier, version);
        }
    }

    public static void applyState(Map<String, String> context, ArtifactState newState) {
        String previous = getStateRaw(context);
        ArtifactState previousState = (previous != null ? ArtifactState.valueOf(previous) : null);
        applyState(s -> context.put(STATE, s.name()), previousState, newState);
    }

    public static void applyState(Consumer<ArtifactState> consumer, ArtifactState previousState, ArtifactState newState) {
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
