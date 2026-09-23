package io.apicurio.registry.storage.dto;

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
@EqualsAndHashCode
@ToString
public class SchemaUsageSummaryDto {

    private long globalId;
    private String version;
    private long totalFetches;
    private int uniqueClients;
    private long firstFetchedOn;
    private long lastFetchedOn;
    private String clientList;
}
