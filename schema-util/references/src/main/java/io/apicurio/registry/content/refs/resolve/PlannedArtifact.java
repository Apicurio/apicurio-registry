package io.apicurio.registry.content.refs.resolve;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * An artifact that is planned to be registered, along with its resolved references.
 */
public final class PlannedArtifact implements ReferenceTarget {

    private final SchemaSource source;
    private final ArtifactCoordinates coordinates;
    private final String artifactType;
    private final String contentType;
    private final RootArtifact root;
    private final List<PlannedReference> references = new ArrayList<>();

    PlannedArtifact(SchemaSource source, ArtifactCoordinates coordinates, String artifactType,
            String contentType, RootArtifact root) {
        this.source = source;
        this.coordinates = coordinates;
        this.artifactType = artifactType;
        this.contentType = contentType;
        this.root = root;
    }

    void setReferences(List<PlannedReference> refs) {
        references.clear();
        references.addAll(refs);
        references.sort(Comparator.comparing(PlannedReference::name));
    }

    /**
     * Returns the schema source.
     *
     * @return the source
     */
    public SchemaSource source() {
        return source;
    }

    /**
     * Returns the coordinates to register under.
     *
     * @return the coordinates
     */
    public ArtifactCoordinates coordinates() {
        return coordinates;
    }

    /**
     * Returns the artifact type.
     *
     * @return the artifact type
     */
    public String artifactType() {
        return artifactType;
    }

    /**
     * Returns the content type.
     *
     * @return the content type
     */
    public String contentType() {
        return contentType;
    }

    /**
     * Indicates whether this artifact was requested as a root.
     *
     * @return true for roots
     */
    public boolean isRoot() {
        return root != null;
    }

    /**
     * Returns the root definition.
     *
     * @return the root artifact, or null for referenced (child) artifacts
     */
    public RootArtifact root() {
        return root;
    }

    /**
     * Returns the references of this artifact, sorted by name.
     *
     * @return an unmodifiable list of references
     */
    public List<PlannedReference> references() {
        return Collections.unmodifiableList(references);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "PlannedArtifact[" + coordinates + ", " + source.location() + "]";
    }
}
