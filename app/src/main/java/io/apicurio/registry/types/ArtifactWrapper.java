package io.apicurio.registry.types;

/**
 * @author Ales Justin
 */
public class ArtifactWrapper {
    private Object artifactImpl;
    private String canonicalString;

    public ArtifactWrapper(Object artifactImpl, String canonicalString) {
        this.artifactImpl = artifactImpl;
        this.canonicalString = canonicalString;
    }

    public <T> T toExactImpl(Class<T> artifactImplType) {
        return artifactImplType.cast(artifactImpl);
    }

    public Object getArtifactImpl() {
        return artifactImpl;
    }

    public String getCanonicalString() {
        return canonicalString;
    }
}
