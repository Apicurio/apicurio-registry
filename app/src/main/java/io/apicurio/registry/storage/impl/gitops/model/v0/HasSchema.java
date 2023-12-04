package io.apicurio.registry.storage.impl.gitops.model.v0;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;


@SuperBuilder
@NoArgsConstructor
@Setter
@Getter
@EqualsAndHashCode
@ToString
public class HasSchema {

    @JsonProperty("$type")
    private String type;
}
