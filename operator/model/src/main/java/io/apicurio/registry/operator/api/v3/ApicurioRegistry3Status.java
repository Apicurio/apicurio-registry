package io.apicurio.registry.operator.api.v3;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.apicurio.registry.operator.api.v3.status.Conditions;
import io.apicurio.registry.operator.api.v3.status.Info;
import io.fabric8.kubernetes.api.model.KubernetesResource;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "conditions", "info" })
@JsonDeserialize(using = JsonDeserializer.None.class)
public class ApicurioRegistry3Status implements KubernetesResource {

    /**
     * Conditions: Apicurio Registry application and Operator conditions.
     */
    @JsonProperty("conditions")
    @JsonPropertyDescription("Conditions: \n Apicurio Registry application and Operator conditions.")
    @JsonSetter(nulls = Nulls.SKIP)
    private List<Conditions> conditions;

    public List<Conditions> getConditions() {
        return conditions;
    }

    public void setConditions(List<Conditions> conditions) {
        this.conditions = conditions;
    }

    /**
     * Information about the Apicurio Registry application
     */
    @JsonProperty("info")
    @JsonPropertyDescription("Information about the Apicurio Registry application")
    @JsonSetter(nulls = Nulls.SKIP)
    private Info info;

    public Info getInfo() {
        return info;
    }

    public void setInfo(Info info) {
        this.info = info;
    }

}
