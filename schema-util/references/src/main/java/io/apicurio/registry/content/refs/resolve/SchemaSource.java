package io.apicurio.registry.content.refs.resolve;

import io.apicurio.registry.content.TypedContent;

import java.util.List;
import java.util.Optional;

/**
 * A single schema document participating in reference resolution.
 */
public interface SchemaSource {

    /**
     * Returns the primary location of this source.
     *
     * @return a normalized POSIX path, without a leading "./" or "/"
     */
    String location();

    /**
     * Returns the content of this source.
     *
     * @return the typed content
     */
    TypedContent content();

    /**
     * Returns the explicit artifact type of this source, if known.
     *
     * @return the artifact type, or empty when it must be detected
     */
    Optional<String> artifactType();

    /**
     * Returns every location under which this source may be referenced. The primary location comes first.
     *
     * @return the list of aliases
     */
    default List<String> aliases() {
        return List.of(location());
    }
}
