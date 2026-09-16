package io.apicurio.registry.storage.impl.kafkasql.messages;

import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.impl.kafkasql.AbstractMessage;
import io.apicurio.registry.types.VersionState;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateArtifactVersionStatesMessage extends AbstractMessage {
    private String groupId;
    private String artifactId;
    private List<String> versions;
    private VersionState newState;
    private String labelPrefix;
    private Map<String, String> labels;

    @Override
    public Object dispatchTo(RegistryStorage storage) {
        storage.updateArtifactVersionStates(groupId, artifactId, versions, newState, labelPrefix, labels);
        return null;
    }
}
