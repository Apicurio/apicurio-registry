package io.apicurio.registry.storage.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Data transfer object representing a peer registry that this registry can federate with.
 * Peers are admin-managed and store credential references only, never secret values.
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@EqualsAndHashCode
@ToString
@RegisterForReflection
public class PeerDto {

    private String peerId;
    private String url;
    private String name;
    private String description;
    private boolean enabled;
    private String credentialSecretRef;
}
