package io.apicurio.registry.storage.impl.kafkasql.values;

import io.apicurio.registry.storage.impl.kafkasql.MessageType;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@RegisterForReflection
@SuperBuilder
@NoArgsConstructor
@Getter
@ToString
public class ArtifactBranchValue extends AbstractMessageValue {


    private String version; // nullable - Not used when deleting
    private Integer branchOrder; // nullable - Used for imports

    public static ArtifactBranchValue create(ActionType action, String version, Integer branchOrder) {
        return ArtifactBranchValue.builder()
                .action(action)
                .version(version)
                .branchOrder(branchOrder)
                .build();
    }


    @Override
    public MessageType getType() {
        return MessageType.ArtifactBranch;
    }
}
