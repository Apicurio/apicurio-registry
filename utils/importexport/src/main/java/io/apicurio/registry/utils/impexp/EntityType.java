package io.apicurio.registry.utils.impexp;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public enum EntityType {

    Manifest, GlobalRule, Content, Group, ArtifactVersion, ArtifactRule, Comment, Branch

}
