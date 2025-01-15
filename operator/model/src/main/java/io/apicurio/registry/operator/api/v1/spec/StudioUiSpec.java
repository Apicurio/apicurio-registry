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
@JsonPropertyOrder({ "enabled", "env", "ingress", "podTemplateSpec" })
@NoArgsConstructor
@AllArgsConstructor(access = PRIVATE)
@SuperBuilder(toBuilder = true)
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class StudioUiSpec extends ComponentSpec {

    /**
     * Enable deployment of the Studio UI component.
     * <p>
     * Defaults to <code>false</code>.
     */
    @JsonProperty("enabled")
    @JsonPropertyDescription("""
            Enable deployment of the Apicurio Studio UI component.

            Defaults to `false`.
            """)
    @JsonSetter(nulls = SKIP)
    private Boolean enabled;
}
