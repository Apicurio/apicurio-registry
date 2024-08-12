package io.apicurio.registry.operator.api.v3.v1;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.sundr.builder.annotations.Buildable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@Buildable(editableEnabled = false, builderPackage = "io.fabric8.kubernetes.api.builder")
@JsonInclude(NON_NULL)
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class ApicurioRegistry3Spec {

    @JsonPropertyDescription("Configuration for Apicurio Registry application component")
    private AppSpec app;

    @JsonPropertyDescription("Configuration for Apicurio Registry web UI component")
    private UISpec ui;
}
