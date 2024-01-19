package io.apicurio.registry.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;


@Getter
@EqualsAndHashCode(callSuper = true)
public class GA extends GroupId {

    private final ArtifactId artifactId;


    public GA(String groupId, String artifactId) {
        super(groupId);
        this.artifactId = new ArtifactId(artifactId);
    }


    public String getRawArtifactId() {
        return artifactId.getRawArtifactId();
    }


    @Override
    public String toString() {
        return super.toString() + ":" + artifactId;
    }
}
