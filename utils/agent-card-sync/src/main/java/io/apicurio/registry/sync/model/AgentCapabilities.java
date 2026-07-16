package io.apicurio.registry.sync.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentCapabilities {
    private boolean streaming;
    private boolean pushNotifications;
}
