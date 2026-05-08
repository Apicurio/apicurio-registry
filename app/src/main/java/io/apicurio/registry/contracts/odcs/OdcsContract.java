package io.apicurio.registry.contracts.odcs;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OdcsContract {
    private String apiVersion;
    private String kind;
    private String id;
    private OdcsInfo info;
    private List<OdcsSchema> schemas;
    private OdcsQuality quality;
    private OdcsServiceLevel serviceLevel;
    private OdcsTeam team;
    private Map<String, Object> customProperties;
}
