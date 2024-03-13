package io.apicurio.registry.storage.impl.kafkasql.messages;

import io.apicurio.registry.model.BranchId;
import io.apicurio.registry.model.GAV;
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
public class CreateOrUpdateArtifactBranch2Message extends AbstractMessage {

    private String groupId;
    private String artifactId;
    private String version;
    private String branchId;

    /**
     * @see io.apicurio.registry.storage.impl.kafkasql.KafkaSqlMessage#dispatchTo(io.apicurio.registry.storage.RegistryStorage)
     */
    @Override
    public Object dispatchTo(RegistryStorage storage) {
        GAV gav = new GAV(groupId, artifactId, version);
        storage.createOrUpdateArtifactBranch(gav, new BranchId(branchId));
        return null;
    }

}
