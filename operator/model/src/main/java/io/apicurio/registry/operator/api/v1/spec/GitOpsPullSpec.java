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
@JsonPropertyOrder({ "sshKeys", "knownHosts" })
@NoArgsConstructor
@AllArgsConstructor(access = PRIVATE)
@SuperBuilder(toBuilder = true)
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class GitOpsPullSpec {

    @JsonProperty("sshKeys")
    @JsonPropertyDescription("""
            Reference to a Secret containing the SSH private key for authenticating \
            to remote Git servers. The key defaults to `id_ed25519`.""")
    @JsonSetter(nulls = SKIP)
    private SecretKeyRef sshKeys;

    @JsonProperty("knownHosts")
    @JsonPropertyDescription("""
            Reference to a Secret containing a known_hosts file for verifying \
            remote Git server host keys. The key defaults to `known_hosts`.""")
    @JsonSetter(nulls = SKIP)
    private SecretKeyRef knownHosts;
}
