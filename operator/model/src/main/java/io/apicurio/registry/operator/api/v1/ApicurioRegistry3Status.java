package io.apicurio.registry.operator.api.v1;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.apicurio.registry.operator.api.v1.status.Conditions;
import io.apicurio.registry.operator.api.v1.status.Info;
import io.javaoperatorsdk.operator.api.ObservedGenerationAwareStatus;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder({ "conditions", "info" })
@JsonDeserialize(using = JsonDeserializer.None.class)
@Getter
@Setter
@ToString
public class ApicurioRegistry3Status extends ObservedGenerationAwareStatus {

    /**
     * Conditions: Apicurio Registry application and Operator conditions.
     */
    @JsonProperty("conditions")
    @JsonPropertyDescription("Conditions: \n Apicurio Registry application and Operator conditions.")
    @JsonSetter(nulls = Nulls.SKIP)
    private List<Conditions> conditions;

    /**
     * Information about the Apicurio Registry application
     */
    @JsonProperty("info")
    @JsonPropertyDescription("Information about the Apicurio Registry application")
    @JsonSetter(nulls = Nulls.SKIP)
    private Info info;
}
