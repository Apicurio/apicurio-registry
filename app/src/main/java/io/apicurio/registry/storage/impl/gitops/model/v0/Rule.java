package io.apicurio.registry.storage.impl.gitops.model.v0;

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
public class Rule {

    private String type;

    private boolean enabled;

    private String config;
}
