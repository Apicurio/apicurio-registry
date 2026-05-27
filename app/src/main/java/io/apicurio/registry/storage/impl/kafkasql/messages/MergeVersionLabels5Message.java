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
public class MergeVersionLabels5Message extends AbstractMessage {

    private String groupId;
    private String artifactId;
    private String version;
    private String prefix;
    private Map<String, String> labels;

    @Override
    public Object dispatchTo(RegistryStorage storage) {
        storage.mergeVersionLabels(groupId, artifactId, version, prefix, labels);
        return null;
    }
}
