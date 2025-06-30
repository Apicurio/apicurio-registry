package io.apicurio.registry.operator.api.v1.spec;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.JsonDeserializer.None;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonDeserialize(using = None.class)
@JsonInclude(NON_NULL)
// Keep properties and fields alphabetical
@JsonPropertyOrder(alphabetic = true)
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class UiSpec extends ComponentSpec {

    /**
     * Indicates whether the operator should deploy the UI component. Defaults to <code>true</code>.
     */
    @JsonProperty("enabled")
    @JsonPropertyDescription("""
            Indicates whether the operator should deploy the UI component.  Defaults to `true`.
            """)
    @JsonSetter(nulls = Nulls.SKIP)
    private Boolean enabled;
}
