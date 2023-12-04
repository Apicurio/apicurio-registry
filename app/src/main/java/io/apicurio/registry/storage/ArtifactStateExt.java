package io.apicurio.registry.storage;

import io.apicurio.registry.storage.error.InvalidArtifactStateException;
import io.apicurio.registry.types.ArtifactState;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;


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
        if (states != null && !states.contains(state)) {
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
        if ( previousState != newState) {
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
}
