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
@JsonPropertyOrder({ "enabled", "appClientId", "uiClientId", "redirectUri", "authServerUrl", "logoutUrl",
        "anonymousReads", "basicAuth", "tls", "authz" })
@NoArgsConstructor
@AllArgsConstructor(access = PRIVATE)
@SuperBuilder(toBuilder = true)
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class AuthSpec {

    @JsonProperty("enabled")
    @JsonPropertyDescription("""
            Enable Apicurio Registry Authentication.
            In Identity providers like Keycloak, this is the client id used for the Quarkus backend application""")
    @JsonSetter(nulls = Nulls.SKIP)
    private Boolean enabled;

    @JsonProperty("appClientId")
    @JsonPropertyDescription("""
            Apicurio Registry backend clientId used for OIDC authentication.
            In Identity providers like Keycloak, this is the client id used for the Quarkus backend application""")
    @JsonSetter(nulls = Nulls.SKIP)
    private String appClientId;

    @JsonProperty("uiClientId")
    @JsonPropertyDescription("""
            Apicurio Registry UI clientId used for OIDC authentication.
            In Identity providers like Keycloak, this is the client id used for the frontend React application""")
    @JsonSetter(nulls = Nulls.SKIP)
    private String uiClientId;

    @JsonProperty("redirectUri")
    @JsonPropertyDescription("""
            Apicurio Registry UI redirect URI used for redirection after successful authentication.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private String redirectUri;

    @JsonProperty("authServerUrl")
    @JsonPropertyDescription("""
            URL of the identity server.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private String authServerUrl;

    @JsonProperty("logoutUrl")
    @JsonPropertyDescription("""
            Apicurio Registry UI redirect URI used for redirection after logout.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private String logoutUrl;

    @JsonProperty("anonymousReads")
    @JsonPropertyDescription("""
            To allow anonymous users, such as REST API calls with no authentication credentials, to make read-only calls to the REST API, set the following option to true.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private Boolean anonymousReads;

    @JsonProperty("basicAuth")
    @JsonPropertyDescription("""
            Client credentials basic auth configuration.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private BasicAuthSpec basicAuth;

    @JsonProperty("tls")
    @JsonPropertyDescription("""
            OIDC TLS configuration.
            When custom certificates are used, this is the field to be used to configure the trustore""")
    @JsonSetter(nulls = Nulls.SKIP)
    private AuthTLSSpec tls;

    @JsonProperty("authz")
    @JsonPropertyDescription("""
            Authorization configuration.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private AuthzSpec authz;
}
