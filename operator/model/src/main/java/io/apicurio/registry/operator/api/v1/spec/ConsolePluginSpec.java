package io.apicurio.registry.operator.api.v1.spec;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.JsonDeserializer.None;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static lombok.AccessLevel.PRIVATE;

@JsonDeserialize(using = None.class)
@JsonInclude(NON_NULL)
@JsonPropertyOrder(alphabetic = true)
@NoArgsConstructor
@AllArgsConstructor(access = PRIVATE)
@Builder
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class ConsolePluginSpec {

    /**
     * Indicates whether the operator should deploy the OpenShift Console plugin.
     * The plugin is only deployed on OpenShift clusters. Defaults to <code>true</code>.
     */
    @JsonProperty("enabled")
    @JsonPropertyDescription("""
            Indicates whether the operator should deploy the OpenShift Console plugin. \
            The plugin is only deployed on OpenShift clusters. Defaults to `true`.
            """)
    @JsonSetter(nulls = Nulls.SKIP)
    private Boolean enabled;
}
