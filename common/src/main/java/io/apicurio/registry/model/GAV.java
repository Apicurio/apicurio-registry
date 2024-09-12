package io.apicurio.registry.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = true)
public final class GAV extends GA {

    private final VersionId versionId;

    public GAV(String rawGroupId, String rawArtifactId, String rawVersionId) {
        super(rawGroupId, rawArtifactId);
        this.versionId = new VersionId(rawVersionId);
    }

    public GAV(GA ga, VersionId versionId) {
        super(ga.getRawGroupId(), ga.getRawArtifactId());
        this.versionId = versionId;
    }

    public GAV(GA ga, String rawVersionId) {
        this(ga, new VersionId(rawVersionId));
    }

    public String getRawVersionId() {
        return versionId.getRawVersionId();
    }

    @Override
    public String toString() {
        return super.toString() + ":" + versionId;
    }
}
