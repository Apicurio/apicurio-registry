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

import static lombok.AccessLevel.PRIVATE;

@JsonDeserialize(using = JsonDeserializer.None.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"source", "admin", "developer", "readOnly"})
@NoArgsConstructor
@AllArgsConstructor(access = PRIVATE)
@SuperBuilder(toBuilder = true)
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class AuthzRolesSpec {

    @JsonProperty("source")
    @JsonPropertyDescription("""
            When set to token, user roles are taken from the authentication token.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private String source;

    @JsonProperty("admin")
    @JsonPropertyDescription("""
            The name of the role that indicates a user is an admin.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private String admin;

    @JsonProperty("developer")
    @JsonPropertyDescription("""
            The name of the role that indicates a user is a developer.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private String developer;

    @JsonProperty("readOnly")
    @JsonPropertyDescription("""
            The name of the role that indicates a user has read-only access.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private String readOnly;
}