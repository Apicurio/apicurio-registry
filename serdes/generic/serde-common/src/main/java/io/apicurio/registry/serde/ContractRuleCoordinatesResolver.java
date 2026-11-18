package io.apicurio.registry.serde;

import io.apicurio.registry.resolver.client.RegistryClientFacade;
import io.apicurio.registry.resolver.client.RegistryVersionCoordinates;
import io.apicurio.registry.resolver.strategy.ArtifactReference;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Finds the coordinates (group ID, artifact ID, version) of the schema version a record was written with,
 * so that consumer-side contract rules can be loaded for it. Records usually carry only a global ID or a
 * content ID, and resolving a schema by ID does not tell its coordinates.
 * <ul>
 * <li>A global ID identifies one version, which the registry is asked for.</li>
 * <li>A content ID can be shared by several versions of several artifacts. The artifact is named with the
 * artifact resolver strategy, as the serializer does, and the registry is asked for the version of that
 * artifact with that content.</li>
 * </ul>
 * Results, including "not found", are cached per ID, so that the registry is asked once per ID and the
 * warning for a record without coordinates is logged once per ID.
 */
class ContractRuleCoordinatesResolver {

    private static final Logger LOG = Logger.getLogger(ContractRuleCoordinatesResolver.class.getName());

    private record ContentKey(long contentId, String groupId, String artifactId) {
    }

    private final Supplier<RegistryClientFacade> facadeSupplier;
    private final ConcurrentHashMap<Long, Optional<ArtifactReference>> byGlobalId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ContentKey, Optional<ArtifactReference>> byContentId =
            new ConcurrentHashMap<>();

    ContractRuleCoordinatesResolver(Supplier<RegistryClientFacade> facadeSupplier) {
        this.facadeSupplier = facadeSupplier;
    }

    /**
     * @param reference the reference the record was read with
     * @param strategyReference names the artifact of the record with the artifact resolver strategy; only
     *            called for records identified by content ID
     * @return the coordinates of the version, or empty if they cannot be determined (a warning is logged)
     */
    Optional<ArtifactReference> resolve(ArtifactReference reference,
            Supplier<ArtifactReference> strategyReference) {
        if (reference.getArtifactId() != null && !reference.getArtifactId().isEmpty()) {
            return Optional.of(reference);
        }
        Long globalId = positive(reference.getGlobalId());
        if (globalId != null) {
            return byGlobalId.computeIfAbsent(globalId, id -> found(
                    facade().getVersionCoordinatesByGlobalId(id), "global ID " + id));
        }
        Long contentId = positive(reference.getContentId());
        if (contentId != null) {
            ArtifactReference artifact = strategyReference.get();
            if (artifact == null || artifact.getArtifactId() == null) {
                LOG.warning("Consumer-side contract rules are skipped for a record with content ID " + contentId
                        + ": the artifact resolver strategy does not name its artifact");
                return Optional.empty();
            }
            ContentKey key = new ContentKey(contentId, artifact.getGroupId(), artifact.getArtifactId());
            return byContentId.computeIfAbsent(key, k -> found(
                    facade().getVersionCoordinatesByContentId(k.contentId(), k.groupId(), k.artifactId()),
                    "content ID " + k.contentId() + " in artifact " + k.groupId() + "/" + k.artifactId()));
        }
        LOG.warning("Consumer-side contract rules are skipped for a record without a global ID, "
                + "content ID or artifact coordinates");
        return Optional.empty();
    }

    private RegistryClientFacade facade() {
        RegistryClientFacade facade = facadeSupplier.get();
        if (facade == null) {
            throw new IllegalStateException("No registry client is configured");
        }
        return facade;
    }

    private static Optional<ArtifactReference> found(RegistryVersionCoordinates coordinates, String what) {
        if (coordinates == null || coordinates.getArtifactId() == null) {
            LOG.warning("Consumer-side contract rules are skipped for records with " + what
                    + ": no schema version was found for it");
            return Optional.empty();
        }
        return Optional.of(ArtifactReference.builder()
                .globalId(coordinates.getGlobalId())
                .contentId(coordinates.getContentId())
                .groupId(coordinates.getGroupId())
                .artifactId(coordinates.getArtifactId())
                .version(coordinates.getVersion())
                .build());
    }

    private static Long positive(Long id) {
        return id != null && id > 0 ? id : null;
    }
}
