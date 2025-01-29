package io.apicurio.registry.operator.api.v1.spec;

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
@JsonPropertyOrder({ "allowDeletes" })
@NoArgsConstructor
@AllArgsConstructor(access = PRIVATE)
@SuperBuilder(toBuilder = true)
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class AppAuthSpec {

    @JsonProperty("authEnabled")
    @JsonPropertyDescription("""
            Enable Apicurio Registry Authentication.
            In Identity providers like Keycloak, this is the client id used for the Quarkus backend application""")
    @JsonSetter(nulls = Nulls.SKIP)
    private Boolean authEnabled;

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

    @JsonProperty("authServerUrl")
    @JsonPropertyDescription("""
            URL of the identity server.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private String authServerUrl;
}
