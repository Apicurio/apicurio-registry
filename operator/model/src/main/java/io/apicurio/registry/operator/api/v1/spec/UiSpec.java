package io.apicurio.registry.operator.api.v1.spec;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JsonDeserializer.None;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.*;
import lombok.experimental.SuperBuilder;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonDeserialize(using = None.class)
@JsonInclude(NON_NULL)
@JsonPropertyOrder({ "env", "ingress", "podTemplateSpec" })
@NoArgsConstructor
// @AllArgsConstructor(access = PRIVATE)
@SuperBuilder(toBuilder = true)
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class UiSpec extends ComponentSpec {
}
