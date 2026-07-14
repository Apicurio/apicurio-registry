package io.apicurio.registry.operator.api.v1.spec.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static lombok.AccessLevel.PRIVATE;

@JsonDeserialize(using = JsonDeserializer.None.class)
@JsonInclude(NON_NULL)
@JsonPropertyOrder({"enabled", "apiAudiences", "cacheExpiration", "groupMapping"})
@NoArgsConstructor
@AllArgsConstructor(access = PRIVATE)
@SuperBuilder(toBuilder = true)
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class KubernetesAuthSpec {

    @JsonProperty("enabled")
    @JsonPropertyDescription("""
            Enable Kubernetes TokenReview authentication. When enabled, Bearer tokens in
            incoming requests are validated via the Kubernetes TokenReview API, eliminating
            the need for an external OIDC provider.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private Boolean enabled;

    @JsonProperty("apiAudiences")
    @JsonPropertyDescription("""
            Comma-separated list of API audiences for TokenReview validation.
            If empty, audience is not validated.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private String apiAudiences;

    @JsonProperty("cacheExpiration")
    @JsonPropertyDescription("""
            TokenReview result cache expiration in minutes. Defaults to 5.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private Integer cacheExpiration;

    @JsonProperty("groupMapping")
    @JsonPropertyDescription("""
            Mapping of Kubernetes groups to Apicurio Registry roles.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private KubernetesGroupMappingSpec groupMapping;
}
