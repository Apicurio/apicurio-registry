package io.apicurio.registry.operator.api.v1.spec;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.JsonDeserializer.None;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.*;
import lombok.experimental.SuperBuilder;

import static lombok.AccessLevel.PRIVATE;

@JsonDeserialize(using = None.class)
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder({ "insecureRequests", "truststoreSecretRef", "truststorePasswordSecretRef", "keystoreSecretRef", "keystorePasswordSecretRef" })
@NoArgsConstructor
@AllArgsConstructor(access = PRIVATE)
@SuperBuilder(toBuilder = true)
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class TLSSpec {

    /**
     * Whether insecure requests are allowed. Default is <code>enabled</code>.
     */
    @JsonProperty("insecureRequests")
    @JsonPropertyDescription("""
            Whether insecure requests are allowed. Default is <code>enabled</code>.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private String insecureRequests;

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

    /**
     * Name of a Secret that contains the TLS keystore (in PKCS12 format). Key <code>ca.p12</code> is
     * assumed by default.
     */
    @JsonProperty("keystoreSecretRef")
    @JsonPropertyDescription("""
            Name of a Secret that contains the TLS keystore (in PKCS12 format). \
            Key `ca.p12` is assumed by default.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private SecretKeyRef keystoreSecretRef;

    /**
     * Name of a Secret that contains the TLS keystore password. Key <code>ca.password</code> is assumed by
     * default.
     */
    @JsonProperty("keystorePasswordSecretRef")
    @JsonPropertyDescription("""
            Name of a Secret that contains the TLS keystore password. \
            Key `ca.password` is assumed by default.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private SecretKeyRef keystorePasswordSecretRef;
}
