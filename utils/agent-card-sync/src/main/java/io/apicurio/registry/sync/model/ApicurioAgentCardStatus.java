package io.apicurio.registry.sync.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApicurioAgentCardStatus {
    private String syncStatus; // e.g., "Active", "Stale"
    private String lastSynced; // Timestamp
    private String message;    // Additional details
}
