package io.apicurio.registry.content.refs.resolve;

import io.apicurio.registry.content.TypedContent;

import java.util.Objects;
import java.util.Optional;

/**
 * A {@link SchemaSource} whose content is held in memory.
 */
public final class InMemorySchemaSource implements SchemaSource {

    private final String location;
    private final TypedContent content;
    private final String artifactType;

    private InMemorySchemaSource(String location, TypedContent content, String artifactType) {
        this.location = location;
        this.content = content;
        this.artifactType = artifactType;
    }

    /**
     * Creates an in-memory source whose artifact type will be detected.
     *
     * @param location the location (normalized via {@link SchemaSourceSet#normalize(String)})
     * @param content the raw content
     * @param contentType the content type
     * @return the source
     */
    public static InMemorySchemaSource of(String location, String content, String contentType) {
        return of(location, content, contentType, null);
    }

    /**
     * Creates an in-memory source with an explicit artifact type.
     *
     * @param location the location (normalized via {@link SchemaSourceSet#normalize(String)})
     * @param content the raw content
     * @param contentType the content type
     * @param artifactType the artifact type, or {@code null} to detect it
     * @return the source
     */
    public static InMemorySchemaSource of(String location, String content, String contentType,
            String artifactType) {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(contentType, "contentType");
        return new InMemorySchemaSource(SchemaSourceSet.normalize(location),
                TypedContent.create(content, contentType), artifactType);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String location() {
        return location;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TypedContent content() {
        return content;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<String> artifactType() {
        return Optional.ofNullable(artifactType);
    }

    @Override
    public String toString() {
        return "InMemorySchemaSource[" + location + "]";
    }
}
