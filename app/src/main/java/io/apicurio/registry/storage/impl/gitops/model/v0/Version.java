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
public class Version {

    private String id;

    private Long globalId;

    private String contentFile;
}
