package io.apicurio.registry.model;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@AllArgsConstructor
@EqualsAndHashCode
public class GA {

    private final String groupId;
    private final String artifactId;

}
