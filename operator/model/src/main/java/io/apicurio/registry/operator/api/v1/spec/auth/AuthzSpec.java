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
@JsonPropertyOrder({ "enabled", "ownerOnlyEnabled", "groupAccessEnabled", "readAccessEnabled", "roleSource", "adminRole",
        "developerRole", "readOnlyRole", "adminOverride" })
@NoArgsConstructor
@AllArgsConstructor(access = PRIVATE)
@SuperBuilder(toBuilder = true)
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class AuthzSpec {

    @JsonProperty("enabled")
    @JsonPropertyDescription("""
            Enabled role-based authorization.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private Boolean enabled;

    @JsonProperty("ownerOnlyEnabled")
    @JsonPropertyDescription("""
            When owner-only authorization is enabled, only the user who created an artifact can modify or delete that artifact.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private Boolean ownerOnlyEnabled;

    @JsonProperty("groupAccessEnabled")
    @JsonPropertyDescription("""
            When owner-only authorization and group owner-only authorization are both enabled, only the user who created an artifact group has write access to that artifact group, for example, to add or remove artifacts in that group.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private Boolean groupAccessEnabled;

    @JsonProperty("readAccessEnabled")
    @JsonPropertyDescription("""
            When the authenticated read access option is enabled, Apicurio Registry grants at least read-only access to requests from any authenticated user in the same organization, regardless of their user role.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private Boolean readAccessEnabled;

    @JsonProperty("roleSource")
    @JsonPropertyDescription("""
            When set to token, user roles are taken from the authentication token.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private String roleSource;

    @JsonProperty("adminRole")
    @JsonPropertyDescription("""
            The name of the role that indicates a user is an admin.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private String adminRole;

    @JsonProperty("developerRole")
    @JsonPropertyDescription("""
            The name of the role that indicates a user is a developer.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private String developerRole;

    @JsonProperty("readOnlyRole")
    @JsonPropertyDescription("""
            The name of the role that indicates a user has read-only access.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private String readOnlyRole;

    @JsonProperty("adminOverride")
    @JsonPropertyDescription("""
            Admin override configuration""")
    @JsonSetter(nulls = Nulls.SKIP)
    private AdminOverrideSpec adminOverride;
}