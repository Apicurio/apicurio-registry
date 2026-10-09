package io.apicurio.registry.storage.impl.polling.model.v0;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@NoArgsConstructor
@Setter
@Getter
@EqualsAndHashCode
@ToString
public class Peer {

    private String peerId;

    private String url;

    private String name;

    private String description;

    private Boolean enabled;

    private String credentialSecretRef;
}
