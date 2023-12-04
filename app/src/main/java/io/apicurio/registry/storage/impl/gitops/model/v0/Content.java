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
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class Content extends HasSchema {

    private String registryId;

    private Long id;

    private String contentHash;

    private String artifactType;

    private String dataFile;
}
