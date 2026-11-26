package io.apicurio.registry.storage.impl.kafkasql.messages;

import com.fasterxml.jackson.annotation.JsonInclude;
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
public class MergeArtifactLabels5Message extends AbstractMessage {

    private String groupId;
    private String artifactId;
    private String prefix;
    private Map<String, String> labels;
    @JsonInclude(value = JsonInclude.Include.NON_NULL, content = JsonInclude.Include.ALWAYS)
    private Map<String, String> expected;

    @Override
    public Object dispatchTo(RegistryStorage storage) {
        storage.mergeArtifactLabels(groupId, artifactId, prefix, labels, expected);
        return null;
    }
}
