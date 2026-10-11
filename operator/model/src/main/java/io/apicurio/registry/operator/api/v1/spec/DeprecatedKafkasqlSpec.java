package io.apicurio.registry.operator.api.v1.spec;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.JsonDeserializer.None;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.*;
import lombok.experimental.SuperBuilder;

import static lombok.AccessLevel.PRIVATE;

@Deprecated(since = "3.0.7")
@JsonDeserialize(using = None.class)
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder({ "bootstrapServers" })
@NoArgsConstructor
@AllArgsConstructor(access = PRIVATE)
@SuperBuilder(toBuilder = true)
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class DeprecatedKafkasqlSpec {

    /**
     * @deprecated Use the <code>app.storage.kafkasql.bootstrapServers</code> field instead. The operator will
     *             attempt to update the field automatically.
     */
    @Deprecated(since = "3.0.7")
    @JsonProperty("bootstrapServers")
    @JsonPropertyDescription("""
            DEPRECATED: Use the `app.storage.kafkasql.bootstrapServers` field instead. \
            The operator will attempt to update the field automatically.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private String bootstrapServers;
}
