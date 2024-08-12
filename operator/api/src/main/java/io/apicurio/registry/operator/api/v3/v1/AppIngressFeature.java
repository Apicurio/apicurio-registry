package io.apicurio.registry.operator.api.v3.v1;

import com.fasterxml.jackson.annotation.JsonInclude;
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
public class AppIngressFeature {

    private Boolean disabled;
}
