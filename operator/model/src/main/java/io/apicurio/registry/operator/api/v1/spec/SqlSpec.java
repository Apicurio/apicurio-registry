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
@JsonPropertyOrder({ "dataSource" })
@NoArgsConstructor
@AllArgsConstructor(access = PRIVATE)
@SuperBuilder(toBuilder = true)
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class SqlSpec {

    /**
     * Configure SQL data source.
     */
    @JsonProperty("dataSource")
    @JsonPropertyDescription("""
            Configure SQL data source.""")
    @JsonSetter(nulls = SKIP)
    private DataSourceSpec dataSource;

    public DataSourceSpec withDataSource() {
        if (dataSource == null) {
            dataSource = new DataSourceSpec();
        }
        return dataSource;
    }
}
