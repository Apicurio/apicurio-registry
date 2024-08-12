package io.apicurio.registry.operator.api.v3.v1;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.javaoperatorsdk.operator.api.ObservedGenerationAwareStatus;
import io.sundr.builder.annotations.Buildable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@Buildable(editableEnabled = false, builderPackage = "io.fabric8.kubernetes.api.builder")
@JsonPropertyOrder({ "info", "conditions" })
@JsonInclude(NON_NULL)
@Getter
@Setter
@EqualsAndHashCode(callSuper = false)
@ToString
public class ApicurioRegistry3Status extends ObservedGenerationAwareStatus {

    private AppStatus app;

    private UIStatus ui;

    private List<StatusConditions> conditions = new ArrayList<>();
}
