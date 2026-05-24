package io.apicurio.registry.storage.impl.kafkasql.messages;

import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.SchemaUsageEventDto;
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
public class RecordUsageEvent1Message extends AbstractMessage {

    private long globalId;
    private long contentId;
    private String clientId;
    private String operation;
    private long eventTimestamp;

    @Override
    public Object dispatchTo(RegistryStorage storage) {
        storage.recordUsageEvent(SchemaUsageEventDto.builder()
                .globalId(globalId)
                .contentId(contentId)
                .clientId(clientId)
                .operation(operation)
                .eventTimestamp(eventTimestamp)
                .build());
        return null;
    }
}
