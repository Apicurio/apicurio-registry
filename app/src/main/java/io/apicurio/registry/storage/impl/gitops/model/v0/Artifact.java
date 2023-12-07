package io.apicurio.registry.storage.impl.gitops.model.v0;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.List;

@SuperBuilder
@NoArgsConstructor
@Setter
@Getter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class Artifact extends HasSchema {

    private String registryId;

    private String groupId;

    private String id;

    private List<Version> versions;

    private List<Rule> rules;
}
