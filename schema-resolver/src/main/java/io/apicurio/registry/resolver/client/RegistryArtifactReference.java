package io.apicurio.registry.resolver.client;

import io.apicurio.registry.resolver.SchemaLookupResult;
import io.apicurio.registry.rest.client.models.ArtifactReference;

public class RegistryArtifactReference {

    private String name;
    private String groupId;
    private String artifactId;
    private String version;

    public static RegistryArtifactReference create(String name, String groupId, String artifactId, String version) {
        RegistryArtifactReference reference = new RegistryArtifactReference();
        reference.setName(name);
        reference.setGroupId(groupId);
        reference.setArtifactId(artifactId);
        reference.setVersion(version);
        return reference;
    }

    public static RegistryArtifactReference fromClientArtifactReference(ArtifactReference ref) {
        return create(ref.getName(), ref.getGroupId(), ref.getArtifactId(), ref.getVersion());
    }

    public static RegistryArtifactReference fromClientArtifactReference(io.apicurio.registry.rest.client.v2.models.ArtifactReference ref) {
        return create(ref.getName(), ref.getGroupId(), ref.getArtifactId(), ref.getVersion());
    }

    public static RegistryArtifactReference fromSchemaLookupResult(SchemaLookupResult<?> refLookup) {
        return create(refLookup.getParsedSchema().referenceName(), refLookup.getGroupId(), refLookup.getArtifactId(),
                refLookup.getVersion());
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}
