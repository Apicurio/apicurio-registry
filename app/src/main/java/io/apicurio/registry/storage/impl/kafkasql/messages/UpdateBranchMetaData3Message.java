package io.apicurio.registry.storage.impl.kafkasql.messages;

import io.apicurio.registry.model.BranchId;
import io.apicurio.registry.model.GA;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.EditableBranchMetaDataDto;
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
public class UpdateBranchMetaData3Message extends AbstractMessage {

    private String groupId;
    private String artifactId;
    private String branchId;
    private EditableBranchMetaDataDto dto;

    /**
     * @see io.apicurio.registry.storage.impl.kafkasql.KafkaSqlMessage#dispatchTo(RegistryStorage)
     */
    @Override
    public Object dispatchTo(RegistryStorage storage) {
        GA ga = new GA(groupId, artifactId);
        storage.updateBranchMetaData(ga, new BranchId(branchId), dto);
        return null;
    }

}
