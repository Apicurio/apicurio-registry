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
@JsonPropertyOrder({ "enabled", "from", "type", "role", "claimName", "claimValue" })
@NoArgsConstructor
@AllArgsConstructor(access = PRIVATE)
@SuperBuilder(toBuilder = true)
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class AdminOverrideSpec {

    @JsonProperty("enabled")
    @JsonPropertyDescription("""
            Auth admin override enabled.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private String enabled;

    @JsonProperty("from")
    @JsonPropertyDescription("""
            Where to look for admin-override information. Only token is currently supported.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private String from;

    @JsonProperty("type")
    @JsonPropertyDescription("""
            The type of information used to determine if a user is an admin. Values depend on the value of the FROM variable, for example, role or claim when FROM is token.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private String type;

    @JsonProperty("role")
    @JsonPropertyDescription("""
            The name of the role that indicates a user is an admin.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private String role;

    @JsonProperty("claimName")
    @JsonPropertyDescription("""
            The name of a JWT token claim to use for determining admin-override.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private String claimName;

    @JsonProperty("claimValue")
    @JsonPropertyDescription("""
            The value that the JWT token claim indicated by the CLAIM variable must be for the user to be granted admin-override.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private String claimValue;
}
