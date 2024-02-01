package io.apicurio.registry.storage.impl.kafkasql.values;

import io.apicurio.registry.storage.impl.kafkasql.MessageType;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.List;

@RegisterForReflection
@SuperBuilder
@NoArgsConstructor
@Getter
@ToString
public class ArtifactBranchValue extends AbstractMessageValue {

    private String version; // nullable - Not used when deleting

    private Integer branchOrder; // nullable - Used for imports

    private List<String> versions; // nullable - Used for ActionType.CREATE_OR_REPLACE

    public static ArtifactBranchValue create(ActionType action, String version, Integer branchOrder, List<String> versions) {
        return ArtifactBranchValue.builder()
                .action(action)
                .version(version)
                .branchOrder(branchOrder)
                .versions(versions)
                .build();
    }


    @Override
    public MessageType getType() {
        return MessageType.ArtifactBranch;
    }
}
