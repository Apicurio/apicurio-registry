package io.apicurio.registry.operator.api.v1.spec.auth;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.JsonDeserializer.None;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.apicurio.registry.operator.api.v1.spec.SecretKeyRef;
import lombok.*;
import lombok.experimental.SuperBuilder;

import static lombok.AccessLevel.PRIVATE;

@JsonDeserialize(using = None.class)
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder({ "tlsVerificationType", "truststoreSecretRef", "truststorePasswordSecretRef" })
@NoArgsConstructor
@AllArgsConstructor(access = PRIVATE)
@SuperBuilder(toBuilder = true)
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class AuthTLSSpec {

    /**
     * Type of TLS verification.
     */
    @JsonProperty("tlsVerificationType")
    @JsonPropertyDescription("""
            Verify the identity server certificate.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private String tlsVerificationType;

    /**
     * Name of a Secret that contains the TLS truststore (in PKCS12 format). Key <code>ca.p12</code> is
     * assumed by default.
     */
    @JsonProperty("truststoreSecretRef")
    @JsonPropertyDescription("""
            Name of a Secret that contains the TLS truststore (in PKCS12 format). \
            Key `ca.p12` is assumed by default.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private SecretKeyRef truststoreSecretRef;

    /**
     * Name of a Secret that contains the TLS truststore password. Key <code>ca.password</code> is assumed by
     * default.
     */
    @JsonProperty("truststorePasswordSecretRef")
    @JsonPropertyDescription("""
            Name of a Secret that contains the TLS truststore password. \
            Key `ca.password` is assumed by default.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private SecretKeyRef truststorePasswordSecretRef;
}
