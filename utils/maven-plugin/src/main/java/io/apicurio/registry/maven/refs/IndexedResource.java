package io.apicurio.registry.maven.refs;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.rest.client.models.ArtifactMetaData;
import io.apicurio.registry.types.ArtifactType;

import java.nio.file.Path;
import java.util.Set;

public class IndexedResource {

    private final Path path;
    private final String type;
    private final String resourceName;
    private final ContentHandle content;
    private ArtifactMetaData registration;

    /**
     * Constructor.
     * 
     * @param path
     * @param type
     * @param resourceName
     * @param content
     */
    public IndexedResource(Path path, String type, String resourceName, ContentHandle content) {
        super();
        this.path = path;
        this.content = content;
        this.type = type;
        this.resourceName = resourceName;
    }

    /**
     * @return the content
     */
    public ContentHandle getContent() {
        return content;
    }

    /**
     * @return the type
     */
    public String getType() {
        return type;
    }

    /**
     * @return the resourceName
     */
    public String getResourceName() {
        return resourceName;
    }

    /**
     * @return the path
     */
    public Path getPath() {
        return path;
    }

    public boolean matches(String resourceName, Path relativeToFile, Set<Path> schemaPaths) {
        // If this is a pre-registered reference, the match has to happen by resource name.
        if (this.path == null) {
            return this.resourceName.equals(resourceName);
        }

        // For Avro files the match can happen either via path (e.g. when referencing an Avro
        // file from an AsyncAPI file) or via resource name (e.g. from Avro to Avro).
        if (ArtifactType.AVRO.equals(this.type)) {
            if (this.resourceName.equals(resourceName)) {
                return true;
            }
        }

        // The resource name will otherwise be a relative path to the resource.
        Path resolvedPath = relativeToFile.getParent().resolve(resourceName);
        boolean resolves = this.path.normalize().equals(resolvedPath.normalize());

        // Protobuf can resolve relative to the "schema paths" (aka --proto-paths in protoc).
        if (!resolves && ArtifactType.PROTOBUF.equals(this.type)) {
            resolves = schemaPaths.parallelStream()
                    .anyMatch(path -> this.path.normalize().equals(path.resolve(resourceName).normalize()));
        }
        return resolves;
    }

    /**
     * @return the registration
     */
    public ArtifactMetaData getRegistration() {
        return registration;
    }

    /**
     * @param registration the registration to set
     */
    public void setRegistration(ArtifactMetaData registration) {
        this.registration = registration;
    }

    public boolean isRegistered() {
        return this.registration != null;
    }
}
