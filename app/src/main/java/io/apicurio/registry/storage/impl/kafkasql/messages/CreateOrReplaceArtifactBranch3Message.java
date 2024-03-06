package io.apicurio.registry.storage.impl.kafkasql.messages;

import java.util.List;
import java.util.stream.Collectors;

import io.apicurio.registry.model.BranchId;
import io.apicurio.registry.model.GA;
import io.apicurio.registry.model.VersionId;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.impl.kafkasql.AbstractMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@EqualsAndHashCode(callSuper = false)
@ToString
public class CreateOrReplaceArtifactBranch3Message extends AbstractMessage {

    private String groupId;
    private String artifactId;
    private String branchId;
    private List<String> versions;

    /**
     * @see io.apicurio.registry.storage.impl.kafkasql.KafkaSqlMessage#dispatchTo(io.apicurio.registry.storage.RegistryStorage)
     */
    @Override
    public Object dispatchTo(RegistryStorage storage) {
        GA ga = new GA(groupId, artifactId);
        BranchId bid = new BranchId(branchId);
        List<VersionId> versionIds = versions == null ? List.of() : versions.stream().map(v -> new VersionId(v)).collect(Collectors.toList());
        storage.createOrReplaceArtifactBranch(ga, bid, versionIds);
        return null;
    }

}
