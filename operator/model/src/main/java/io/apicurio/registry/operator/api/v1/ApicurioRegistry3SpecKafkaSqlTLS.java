package io.apicurio.registry.operator.api.v1;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.JsonDeserializer.None;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.fabric8.kubernetes.api.model.KubernetesResource;
import lombok.*;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder({ "keystoreSecretName", "truststoreSecretName" })
@JsonDeserialize(using = None.class)
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Getter
@Setter
@ToString
public class ApicurioRegistry3SpecKafkaSqlTLS implements KubernetesResource {

    /**
     * Name of a Secret that contains TLS keystore (in PKCS12 format) under the `user.p12` key, and keystore
     * password under the `user.password` key.
     */
    @JsonProperty("keystoreSecretName")
    @JsonPropertyDescription("""
            Name of a Secret that contains TLS keystore (in PKCS12 format) under the `user.p12` key, and keystore password under the `user.password` key.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private String keystoreSecretName;

    /**
     * Name of a Secret that contains TLS truststore (in PKCS12 format) under the `ca.p12` key, and truststore
     * password under the `ca.password` key.
     */
    @JsonProperty("truststoreSecretName")
    @JsonPropertyDescription("""
            Name of a Secret that contains TLS truststore (in PKCS12 format) under the `ca.p12` key, and truststore password under the `ca.password` key.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private String truststoreSecretName;
}
