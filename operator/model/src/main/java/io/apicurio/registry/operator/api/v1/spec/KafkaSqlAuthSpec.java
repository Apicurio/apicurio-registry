package io.apicurio.registry.operator.api.v1.spec;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;
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
import static com.fasterxml.jackson.annotation.Nulls.SKIP;
import static lombok.AccessLevel.PRIVATE;

@JsonDeserialize(using = JsonDeserializer.None.class)
@JsonInclude(NON_NULL)
@JsonPropertyOrder({ "enabled", "mechanism", "clientId", "clientSecret", "tokenEndpoint",
        "loginHandlerClass" })
@NoArgsConstructor
@AllArgsConstructor(access = PRIVATE)
@SuperBuilder(toBuilder = true)
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class KafkaSqlAuthSpec {

    @JsonProperty("enabled")
    @JsonPropertyDescription("""
            Enables SASL OAuth authentication for Apicurio Registry storage in Kafka. You must set this variable to true for the other variables to have effect.""")
    @JsonSetter(nulls = SKIP)
    private Boolean enabled;

    @JsonProperty("mechanism")
    @JsonPropertyDescription("""
            The mechanism used to authenticate to Kafka.""")
    @JsonSetter(nulls = SKIP)
    private String mechanism;

    @JsonProperty("clientId")
    @JsonPropertyDescription("""
            The client ID used to authenticate to Kafka.""")
    @JsonSetter(nulls = SKIP)
    private String clientId;

    @JsonProperty("clientSecret")
    @JsonPropertyDescription("""
            The client secret used to authenticate to Kafka.""")
    @JsonSetter(nulls = SKIP)
    private SecretKeyRef clientSecret;

    @JsonProperty("tokenEndpoint")
    @JsonPropertyDescription("""
            The URL of the OAuth identity server.""")
    @JsonSetter(nulls = SKIP)
    private String tokenEndpoint;

    @JsonProperty("loginHandlerClass")
    @JsonPropertyDescription("""
            The login class to be used for login.""")
    @JsonSetter(nulls = SKIP)
    private String loginHandlerClass;
}
