package io.apicurio.registry.operator.api.v1.status;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.JsonDeserializer.None;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.fabric8.crd.generator.annotation.SchemaFrom;
import io.fabric8.generator.annotation.Min;
import io.fabric8.generator.annotation.Pattern;
import io.fabric8.generator.annotation.Required;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.Objects;

import static lombok.AccessLevel.PRIVATE;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder({"type", "status", "reason", "message", "lastTransitionTime", "lastUpdateTime", "observedGeneration"})
@JsonDeserialize(using = None.class)
@NoArgsConstructor
@AllArgsConstructor(access = PRIVATE)
@SuperBuilder(toBuilder = true)
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class Condition {

    /**
     * The last time the condition transitioned from one status to another.
     */
    @JsonProperty("lastTransitionTime")
    @Required()
    @JsonPropertyDescription("""
            The last time the condition transitioned from one status to another.
            """)
    @JsonSetter(nulls = Nulls.SKIP)
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    @SchemaFrom(type = String.class)
    private Instant lastTransitionTime;

    /**
     * The last time that the condition was updated by the operator.
     * Unlike the {@link #lastTransitionTime} field, this timestamp is updated even if the status has not changed.
     */
    @JsonProperty("lastUpdateTime")
    @Required()
    @JsonPropertyDescription("""
            The last time that the condition was updated by the operator.
            Unlike the `lastTransitionTime` field, this timestamp is updated even if the status has not changed.
            """)
    @JsonSetter(nulls = Nulls.SKIP)
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    @SchemaFrom(type = String.class)
    private Instant lastUpdateTime;

    /**
     * message is a human readable message indicating details about the transition. This may be an empty
     * string.
     */
    @JsonProperty("message")
    @Required()
    @JsonPropertyDescription("message is a human readable message indicating details about the transition. This may be an empty string.")
    @JsonSetter(nulls = Nulls.SKIP)
    private String message;

    /**
     * observedGeneration represents the .metadata.generation that the condition was set based upon. For
     * instance, if .metadata.generation is currently 12, but the .status.conditions[x].observedGeneration is
     * 9, the condition is out of date with respect to the current state of the instance.
     */
    @JsonProperty("observedGeneration")
    @Min(0.0)
    @JsonPropertyDescription("observedGeneration represents the .metadata.generation that the condition was set based upon. For instance, if .metadata.generation is currently 12, but the .status.conditions[x].observedGeneration is 9, the condition is out of date with respect to the current state of the instance.")
    @JsonSetter(nulls = Nulls.SKIP)
    private Long observedGeneration;

    /**
     * reason contains a programmatic identifier indicating the reason for the condition's last transition.
     * Producers of specific condition types may define expected values and meanings for this field, and
     * whether the values are considered a guaranteed API. The value should be a CamelCase string. This field
     * may not be empty.
     */
    @JsonProperty("reason")
    @Required()
    @Pattern("^[A-Za-z]([A-Za-z0-9_,:]*[A-Za-z0-9_])?$")
    @JsonPropertyDescription("reason contains a programmatic identifier indicating the reason for the condition's last transition. Producers of specific condition types may define expected values and meanings for this field, and whether the values are considered a guaranteed API. The value should be a CamelCase string. This field may not be empty.")
    @JsonSetter(nulls = Nulls.SKIP)
    private String reason;

    /**
     * status of the condition, one of True, False, Unknown.
     */
    @JsonProperty("status")
    @Required()
    @JsonPropertyDescription("status of the condition, one of True, False, Unknown.")
    @JsonSetter(nulls = Nulls.SKIP)
    private ConditionStatus status;

    /**
     * type of condition in CamelCase or in foo.example.com/CamelCase. --- Many .condition.type values are
     * consistent across resources like Available, but because arbitrary conditions can be useful (see
     * .node.status.conditions), the ability to deconflict is important. The regex it matches is
     * (dns1123SubdomainFmt/)?(qualifiedNameFmt)
     */
    @JsonProperty("type")
    @Required()
    @Pattern("^([a-z0-9]([-a-z0-9]*[a-z0-9])?(\\.[a-z0-9]([-a-z0-9]*[a-z0-9])?)*/)?(([A-Za-z0-9][-A-Za-z0-9_.]*)?[A-Za-z0-9])$")
    @JsonPropertyDescription("type of condition in CamelCase or in foo.example.com/CamelCase. --- Many .condition.type values are consistent across resources like Available, but because arbitrary conditions can be useful (see .node.status.conditions), the ability to deconflict is important. The regex it matches is (dns1123SubdomainFmt/)?(qualifiedNameFmt)")
    @JsonSetter(nulls = Nulls.SKIP)
    private String type;

    public static boolean isEquivalent(Condition left, Condition right) {
        return Objects.equals(left.type, right.type) &&
               Objects.equals(left.status, right.status) &&
               Objects.equals(left.reason, right.reason) &&
               Objects.equals(left.message, right.message);
    }
}
