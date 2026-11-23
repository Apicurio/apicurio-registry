package io.apicurio.registry.storage.impl.kafkasql.messages;

import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.impl.kafkasql.AbstractMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Map;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@EqualsAndHashCode(callSuper = false)
@ToString
public class CompareAndSetArtifactLabels4Message extends AbstractMessage {

    private String groupId;
    private String artifactId;
    private Map<String, String> expected;
    private Map<String, String> labels;

    @Override
    public Object dispatchTo(RegistryStorage storage) {
        storage.compareAndSetArtifactLabels(groupId, artifactId, expected, labels);
        return null;
    }
}
