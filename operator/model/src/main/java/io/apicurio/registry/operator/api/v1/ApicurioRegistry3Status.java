package io.apicurio.registry.operator.api.v1;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.JsonDeserializer.None;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.apicurio.registry.operator.api.v1.status.Condition;
import io.apicurio.registry.operator.api.v1.status.Info;
import io.javaoperatorsdk.operator.api.ObservedGenerationAwareStatus;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static com.fasterxml.jackson.annotation.Nulls.SKIP;
import static lombok.AccessLevel.PRIVATE;

@JsonInclude(NON_NULL)
@JsonPropertyOrder({ "conditions", "info" })
@JsonDeserialize(using = None.class)
@NoArgsConstructor
@AllArgsConstructor(access = PRIVATE)
@Builder
@Getter
@Setter
@EqualsAndHashCode(callSuper = false)
@ToString
public class ApicurioRegistry3Status extends ObservedGenerationAwareStatus {

    /**
     * Apicurio Registry operator and operand conditions.
     */
    @JsonProperty("conditions")
    @JsonPropertyDescription("""
            Apicurio Registry operator and operand conditions.""")
    @JsonSetter(nulls = SKIP)
    private List<Condition> conditions = new ArrayList<>();

    @JsonProperty("info")
    @JsonPropertyDescription("Information about the Apicurio Registry application")
    @JsonSetter(nulls = Nulls.SKIP)
    private Info info;
}
