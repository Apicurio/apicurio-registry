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
@JsonPropertyOrder({ "dataSource" })
@NoArgsConstructor
@AllArgsConstructor(access = PRIVATE)
@SuperBuilder(toBuilder = true)
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class DeprecatedSqlSpec {

    /**
     * @deprecated Use the <code>app.storage.sql.dataSource</code> field instead. The operator will attempt to
     *             update the field automatically.
     */
    @Deprecated(since = "3.0.7")
    @JsonProperty("dataSource")
    @JsonPropertyDescription("""
            DEPRECATED: Use the `app.storage.sql.dataSource` field instead. \
            The operator will attempt to update the field automatically.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private DeprecatedDataSource dataSource;
}
