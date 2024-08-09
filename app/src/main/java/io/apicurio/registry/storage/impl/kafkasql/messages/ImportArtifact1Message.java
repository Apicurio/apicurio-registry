package io.apicurio.registry.storage.impl.kafkasql.messages;

import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.impl.kafkasql.AbstractMessage;
import io.apicurio.registry.utils.impexp.v3.ArtifactEntity;
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
public class ImportArtifact1Message extends AbstractMessage {

    private ArtifactEntity entity;

    /**
     * @see io.apicurio.registry.storage.impl.kafkasql.KafkaSqlMessage#dispatchTo(RegistryStorage)
     */
    @Override
    public Object dispatchTo(RegistryStorage storage) {
        storage.importArtifact(entity);
        return null;
    }

}
