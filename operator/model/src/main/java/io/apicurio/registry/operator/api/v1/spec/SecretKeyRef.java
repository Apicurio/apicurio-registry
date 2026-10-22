package io.apicurio.registry.operator.api.v1.spec;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.JsonDeserializer.None;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.*;
import lombok.experimental.SuperBuilder;

import static lombok.AccessLevel.PRIVATE;

// NOTE: We're not using io.fabric8.kubernetes.api.model.SecretKeySelector because the optional field is not needed.
@JsonDeserialize(using = None.class)
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder({ "name", "key" })
@NoArgsConstructor
@AllArgsConstructor(access = PRIVATE)
@SuperBuilder(toBuilder = true)
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class SecretKeyRef {

    /**
     * Name of a Secret that is being referenced.
     */
    @JsonProperty("name")
    @JsonPropertyDescription("""
            Name of a Secret that is being referenced.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private String name;

    /**
     * Name of a key in the referenced Secret that contains the target data. This field might be optional if a
     * default value has been defined.
     */
    @JsonProperty("key")
    @JsonPropertyDescription("""
            Name of the key in the referenced Secret that contain the target data. \
            This field might be optional if a default value has been defined.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private String key;
}
