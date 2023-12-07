package io.apicurio.registry.storage.impl.kafkasql.keys;

import io.apicurio.registry.storage.impl.kafkasql.MessageType;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.*;

@RegisterForReflection
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@EqualsAndHashCode
@ToString
public class ArtifactBranchKey implements MessageKey {


    private String groupId;

    private String artifactId;

    private String branchId;


    public static ArtifactBranchKey create(String groupId, String artifactId, String branchId) {
        return ArtifactBranchKey.builder()
                .groupId(groupId)
                .artifactId(artifactId)
                .branchId(branchId)
                .build();
    }


    @Override
    public MessageType getType() {
        return MessageType.ArtifactBranch;
    }


    @Override
    public String getPartitionKey() {
        return groupId + "/" + artifactId + "/" + branchId;
    }
}
