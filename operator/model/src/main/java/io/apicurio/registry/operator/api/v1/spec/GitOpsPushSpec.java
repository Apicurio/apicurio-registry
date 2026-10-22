package io.apicurio.registry.operator.api.v1.spec;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.JsonDeserializer.None;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static com.fasterxml.jackson.annotation.Nulls.SKIP;
import static lombok.AccessLevel.PRIVATE;

@JsonDeserialize(using = None.class)
@JsonInclude(NON_NULL)
@JsonPropertyOrder({ "authorizedKeys", "hostKey" })
@NoArgsConstructor
@AllArgsConstructor(access = PRIVATE)
@SuperBuilder(toBuilder = true)
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class GitOpsPushSpec {

    @JsonProperty("authorizedKeys")
    @JsonPropertyDescription("""
            Reference to a Secret containing the authorized_keys file that controls \
            which SSH public keys are allowed to push. The key defaults to `authorized_keys`.""")
    @JsonSetter(nulls = SKIP)
    private SecretKeyRef authorizedKeys;

    @JsonProperty("hostKey")
    @JsonPropertyDescription("""
            Reference to a Secret containing a persistent SSH host key for the sidecar's \
            SSH server. Ensures a stable host fingerprint across pod restarts. \
            The key defaults to `ssh_host_key`.""")
    @JsonSetter(nulls = SKIP)
    private SecretKeyRef hostKey;
}
