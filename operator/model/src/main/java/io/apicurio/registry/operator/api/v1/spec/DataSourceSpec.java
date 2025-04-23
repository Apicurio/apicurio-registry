package io.apicurio.registry.operator.api.v1.spec;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.JsonDeserializer.None;
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

@JsonDeserialize(using = None.class)
@JsonInclude(NON_NULL)
@JsonPropertyOrder({"url", "username", "password"})
@NoArgsConstructor
@AllArgsConstructor(access = PRIVATE)
@SuperBuilder(toBuilder = true)
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class DataSourceSpec {

    /**
     * Configure SQL data source URL.
     * <p>
     * Example: <code>jdbc:postgresql://example-postgresql-database:5432/apicurio</code>.
     */
    @JsonProperty("url")
    @JsonPropertyDescription("""
            Configure SQL data source URL.

            Example: `jdbc:postgresql://example-postgresql-database:5432/apicurio`.""")
    @JsonSetter(nulls = SKIP)
    private String url;

    /**
     * Configure SQL data source username.
     */
    @JsonProperty("username")
    @JsonPropertyDescription("""
            Configure SQL data source username.""")
    @JsonSetter(nulls = SKIP)
    private String username;

    /**
     * Configure SQL data source password.
     * <p>
     * References name of a Secret that contains the password. Key <code>password</code> is assumed by default.
     */
    @JsonProperty("password")
    @JsonPropertyDescription("""
            Configure SQL data source password.
                      
            References name of a Secret that contains the password. Key `password` is assumed by default.""")
    @JsonSetter(nulls = SKIP)
    private SecretKeyRef password;
}
