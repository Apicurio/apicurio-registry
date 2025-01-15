package io.apicurio.registry.operator.api.v1.spec;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.*;
import lombok.experimental.SuperBuilder;

import static lombok.AccessLevel.PRIVATE;

@Deprecated(since = "3.0.7")
@JsonDeserialize(using = JsonDeserializer.None.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "url", "username", "password" })
@NoArgsConstructor
@AllArgsConstructor(access = PRIVATE)
@SuperBuilder(toBuilder = true)
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class DeprecatedDataSource {

    /**
     * @deprecated Use the <code>app.storage.sql.dataSource.url</code> field instead. The operator will
     *             attempt to update the field automatically.
     */
    @Deprecated(since = "3.0.7")
    @JsonProperty("url")
    @JsonPropertyDescription("""
            DEPRECATED: Use the `app.storage.sql.dataSource.url` field instead. \
            The operator will attempt to update the field automatically.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private String url;

    /**
     * @deprecated Use the <code>app.storage.sql.dataSource.username</code> field instead. The operator will
     *             attempt to update the field automatically.
     */
    @Deprecated(since = "3.0.7")
    @JsonProperty("username")
    @JsonPropertyDescription("""
            DEPRECATED: Use the `app.storage.sql.dataSource.username` field instead. \
            The operator will attempt to update the field automatically.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private String username;

    /**
     * @deprecated Use the <code>app.storage.sql.dataSource.password</code> field instead. The operator will
     *             attempt to update the field automatically.
     */
    @Deprecated(since = "3.0.7")
    @JsonProperty("password")
    @JsonPropertyDescription("""
            DEPRECATED: Use the `app.storage.sql.dataSource.password` field instead. \
            The operator will attempt to update the field automatically.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private String password;
}
