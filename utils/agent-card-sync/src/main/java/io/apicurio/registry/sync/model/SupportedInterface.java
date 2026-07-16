package io.apicurio.registry.sync.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupportedInterface {
    private String url;
    private String protocolBinding;
    private String protocolVersion;
}
