package io.apicurio.registry.operator.api.v1.spec;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.JsonDeserializer.None;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.*;
import lombok.experimental.SuperBuilder;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static com.fasterxml.jackson.annotation.Nulls.SKIP;
import static lombok.AccessLevel.PRIVATE;

@JsonDeserialize(using = None.class)
@JsonInclude(NON_NULL)
@JsonPropertyOrder({ "url", "username", "password" })
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
     * If you want to reference a Secret, you can set the <code>APICURIO_DATASOURCE_PASSWORD</code>
     * environment variable directly using the <code>app.env</code> field.
     */
    @JsonProperty("password")
    @JsonPropertyDescription("""
            Configure SQL data source password.

            If you want to reference a Secret, you can set the `APICURIO_DATASOURCE_PASSWORD` environment variable \
            directly using the `app.env` field.""")
    @JsonSetter(nulls = SKIP)
    private String password;
}
