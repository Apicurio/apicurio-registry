package io.apicurio.registry.storage;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.slf4j.Logger;

import io.apicurio.registry.storage.error.InvalidVersionStateException;
import io.apicurio.registry.types.VersionState;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class VersionStateExt {

    private static final Map<VersionState, EnumSet<VersionState>> transitions;

    static {
        transitions = new HashMap<>();
        transitions.put(VersionState.ENABLED, EnumSet.of(VersionState.DISABLED, VersionState.DEPRECATED));
        transitions.put(VersionState.DISABLED, EnumSet.of(VersionState.ENABLED, VersionState.DEPRECATED));
        transitions.put(VersionState.DEPRECATED, EnumSet.of(VersionState.ENABLED, VersionState.DISABLED));
    }

    public static final EnumSet<VersionState> ACTIVE_STATES = EnumSet.of(VersionState.ENABLED, VersionState.DEPRECATED, VersionState.DISABLED);

    @Inject
    Logger log;

    public boolean canTransition(VersionState before, VersionState after) {
        EnumSet<VersionState> states = transitions.get(before);
        return states.contains(after);
    }

    public void validateState(EnumSet<VersionState> states, VersionState state, String groupId, String artifactId, String version) {
        if (states != null && !states.contains(state)) {
            throw new InvalidVersionStateException(groupId, artifactId, version, state);
        }
        logIfDeprecated(groupId, artifactId, version, state);
    }

    public void logIfDeprecated(String groupId, Object artifactId, Object version, VersionState state) {
        if (state == VersionState.DEPRECATED) {
            log.warn("Artifact {} [{}] in group ({}) is deprecated", artifactId, version, groupId);
        }
    }

    public void applyState(Consumer<VersionState> consumer, VersionState previousState, VersionState newState) {
        if (previousState != newState) {
            if (previousState != null) {
                if (canTransition(previousState, newState)) {
                    consumer.accept(newState);
                } else {
                    throw new InvalidVersionStateException(previousState, newState);
                }
            } else {
                consumer.accept(newState);
            }
        }
    }
}
