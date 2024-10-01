package io.apicurio.registry.operator.api.v1.status;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.JsonDeserializer.None;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.fabric8.crd.generator.annotation.SchemaFrom;
import io.fabric8.generator.annotation.Min;
import io.fabric8.generator.annotation.Pattern;
import io.fabric8.generator.annotation.Required;
import io.fabric8.kubernetes.api.model.KubernetesResource;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder({ "lastTransitionTime", "message", "observedGeneration", "reason", "status", "type" })
@JsonDeserialize(using = None.class)
@Getter
@Setter
@ToString
public class Conditions implements KubernetesResource {

    /**
     * lastTransitionTime is the last time the condition transitioned from one status to another. This should
     * be when the underlying condition changed. If that is not known, then using the time when the API field
     * changed is acceptable.
     */
    @JsonProperty("lastTransitionTime")
    @Required()
    @JsonPropertyDescription("lastTransitionTime is the last time the condition transitioned from one status to another. This should be when the underlying condition changed.  If that is not known, then using the time when the API field changed is acceptable.")
    @JsonSetter(nulls = Nulls.SKIP)
    @JsonFormat(shape = Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    @SchemaFrom(type = String.class)
    private Instant lastTransitionTime;

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
}
