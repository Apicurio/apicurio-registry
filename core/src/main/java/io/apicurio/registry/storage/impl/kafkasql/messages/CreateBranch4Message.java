package io.apicurio.registry.storage.impl.kafkasql.messages;

import io.apicurio.registry.model.BranchId;
import io.apicurio.registry.model.GA;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.impl.kafkasql.AbstractMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@EqualsAndHashCode(callSuper = false)
@ToString
public class CreateBranch4Message extends AbstractMessage {

    private String groupId;
    private String artifactId;
    private String branchId;
    private String description;
    private List<String> versions;

    /**
     * @see io.apicurio.registry.storage.impl.kafkasql.KafkaSqlMessage#dispatchTo(RegistryStorage)
     */
    @Override
    public Object dispatchTo(RegistryStorage storage) {
        GA ga = new GA(groupId, artifactId);
        return storage.createBranch(ga, new BranchId(branchId), description, versions);
    }

}
