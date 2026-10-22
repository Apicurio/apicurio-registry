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
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static lombok.AccessLevel.PRIVATE;

@JsonDeserialize(using = None.class)
@JsonInclude(NON_NULL)
@JsonPropertyOrder({"enabled", "maxReplicas", "minReplicas", "targetCPUUtilizationPercentage",
        "targetMemoryUtilizationPercentage"})
@NoArgsConstructor
@AllArgsConstructor(access = PRIVATE)
@SuperBuilder(toBuilder = true)
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class AutoscalingSpec {

    @JsonProperty("enabled")
    @JsonPropertyDescription("""
            Whether a HorizontalPodAutoscaler should be managed by the operator. Defaults to 'false'.

            When enabled, the operator creates an HPA targeting the component's Deployment. \
            The static 'replicas' field is ignored when autoscaling is enabled.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private Boolean enabled;

    @JsonProperty("minReplicas")
    @JsonPropertyDescription("""
            Minimum number of replicas for the HorizontalPodAutoscaler. Defaults to 1.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private Integer minReplicas;

    @JsonProperty("maxReplicas")
    @JsonPropertyDescription("""
            Maximum number of replicas for the HorizontalPodAutoscaler. \
            Required when autoscaling is enabled.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private Integer maxReplicas;

    @JsonProperty("targetCPUUtilizationPercentage")
    @JsonPropertyDescription("""
            Target average CPU utilization percentage across all pods. Defaults to 80. \
            The HPA will scale the number of replicas to maintain this target.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private Integer targetCPUUtilizationPercentage;

    @JsonProperty("targetMemoryUtilizationPercentage")
    @JsonPropertyDescription("""
            Target average memory utilization percentage across all pods. \
            If not set, memory-based scaling is not configured.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private Integer targetMemoryUtilizationPercentage;
}
