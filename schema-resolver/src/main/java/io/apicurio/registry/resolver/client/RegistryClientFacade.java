package io.apicurio.registry.resolver.client;

import io.apicurio.registry.resolver.strategy.ArtifactReference;

import java.util.List;
import java.util.Set;

/**
 * This interface represents a layer of abstraction between Registry clients and the
 * Registry server, allowing us to support multiple versions of the Registry Core API
 * at the same time.
 *
 * Use the @{@link RegistryClientFacadeFactory} to create an instance of this interface,
 * allowing integration with an instance of a Registry.
 *
 * <em>Note:</em> This interface is considered internal and therefore can, but
 * probably SHOULD NOT, be implemented outside the apicurio-registry codebase.
 */
public interface RegistryClientFacade extends AutoCloseable {

    /*
     * Methods to get the content of a Schema by various identifiers.
     */

    String getSchemaByContentId(Long contentId);
    String getSchemaByGlobalId(long globalId, boolean dereferenced);
    String getSchemaByGAV(String groupId, String artifactId, String version);
    String getSchemaByContentHash(String contentHash);

    /*
     * Methods to get the references of a Schema by various identifiers.
     */

    List<RegistryArtifactReference> getReferencesByContentId(long contentId);
    List<RegistryArtifactReference> getReferencesByGlobalId(long globalId);
    List<RegistryArtifactReference> getReferencesByGAV(String groupId, String artifactId, String version);
    List<RegistryArtifactReference> getReferencesByContentHash(String contentHash);

    /**
     * Get a list of versions (for a specific artifact) that refer to a schema with the given content.
     */
    List<RegistryVersionCoordinates> searchVersionsByContent(String schemaString, String artifactType,
            ArtifactReference reference, boolean canonical);

    /**
     * Create a new schema.
     * <p>
     * NOTE: References must not be repeated, so we require a set instead of a list to protect against that.
     */
    RegistryVersionCoordinates createSchema(String artifactType, String groupId, String artifactId, String version,
                                            String autoCreateBehavior, boolean canonical, String schemaString,
                                            Set<RegistryArtifactReference> references);

    /**
     * Gets "full" coordinates (includes all IDs) of a schema version given its GAV coordinates.
     */
    RegistryVersionCoordinates getVersionCoordinatesByGAV(String groupId, String artifactId, String version);

    /**
     * Access the underlying (generated) client used by the facade to access the Registry API.  For
     * example, this might return an instance of {@link io.apicurio.registry.rest.client.RegistryClient}.
     */
    Object getClient();
}
