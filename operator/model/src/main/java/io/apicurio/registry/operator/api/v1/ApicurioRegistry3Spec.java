package io.apicurio.registry.operator.api.v1;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.JsonDeserializer.None;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.fabric8.kubernetes.api.model.KubernetesResource;
import lombok.*;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder({ "app", "ui", "studioUi" })
@JsonDeserialize(using = None.class)
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Getter
@Setter
@ToString
public class ApicurioRegistry3Spec implements KubernetesResource {

    /**
     * Configuration specific to Apicurio Registry backend component.
     */
    @JsonProperty("app")
    @JsonPropertyDescription("Configuration specific to Apicurio Registry backend component.")
    @JsonSetter(nulls = Nulls.SKIP)
    private ApicurioRegistry3SpecApp app = new ApicurioRegistry3SpecApp();

    /**
     * Configuration specific to Apicurio Registry UI component.
     */
    @JsonProperty("ui")
    @JsonPropertyDescription("Configuration specific to Apicurio Registry UI component.")
    @JsonSetter(nulls = Nulls.SKIP)
    private ApicurioRegistry3SpecUI ui = new ApicurioRegistry3SpecUI();

    /**
     * Configuration specific to Apicurio Registry UI component.
     */
    @JsonProperty("studioUi")
    @JsonPropertyDescription("Configuration specific to Apicurio Studio UI component.")
    @JsonSetter(nulls = Nulls.SKIP)
    private ApicurioRegistry3SpecStudioUI studioUi = new ApicurioRegistry3SpecStudioUI();
}
