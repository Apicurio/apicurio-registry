package io.apicurio.registry.rest.cache.strategy;

import io.apicurio.registry.rest.cache.etag.ETagBuilder;
import io.apicurio.registry.rest.cache.etag.ETagKeys;
import io.apicurio.registry.rest.v3.beans.HandleReferencesType;
import io.apicurio.registry.types.ReferenceType;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;
import java.util.function.Supplier;

import static io.apicurio.registry.rest.cache.Cacheability.HIGH;
import static io.apicurio.registry.rest.cache.Cacheability.LOW;
import static io.apicurio.registry.rest.cache.Cacheability.MODERATE;
import static io.apicurio.registry.rest.cache.Cacheability.min;
import static io.apicurio.registry.rest.v3.beans.HandleReferencesType.PRESERVE;
import static io.apicurio.registry.types.ReferenceType.INBOUND;
import static java.util.Objects.requireNonNull;
import static lombok.AccessLevel.PRIVATE;

@AllArgsConstructor(access = PRIVATE)
@Builder
public class EntityIdContentCacheStrategy extends CacheStrategy {

    // IMPORTANT: Any of the non-required parameters can be null. Be careful about default values.

    private final Object entityId; // Required. Content ID is a Long, content hash is a String...
    private final HandleReferencesType references;
    /**
     * Etag has to change if any references are in a DRAFT state and are being updated.
     * It's more expensive to compute, so we do so on demand.
     */
    private final Supplier<List<Long>> referenceTreeContentIds;
    private final ReferenceType refType;
    private final Boolean returnArtifactType;

    @Override
    public void evaluate() {
        requireNonNull(entityId);

        eTagBuilder = new ETagBuilder()
                .with(ETagKeys.ENTITY_ID, entityId);
        cacheability = HIGH;

        if (references != null) {
            eTagBuilder.with(ETagKeys.QUERY_PARAM_REFERENCES, references);
            if (!PRESERVE.equals(references) && isVersionMutabilityEnabled()) {
                if (referenceTreeContentIds != null && isHigherQualityEtagEnabled()) {
                    eTagBuilder.with(ETagKeys.REFERENCE_TREE_CONTENT_IDS, referenceTreeContentIds.get());
                    cacheability = min(cacheability, MODERATE);
                } else {
                    eTagBuilder.withRandom();
                    cacheability = min(cacheability, LOW);
                }
            }
        } // Default is PRESERVE

        if (refType != null) {
            eTagBuilder.with(ETagKeys.QUERY_PARAM_REF_TYPE, refType);
            // INBOUND references can change when new artifact versions are created
            // or draft versions are updated that reference this content.
            if (INBOUND.equals(refType)) {
                eTagBuilder.withRandom();
                cacheability = min(cacheability, LOW);
            }
        } // Default is OUTBOUND

        if (returnArtifactType != null) {
            // Doesn't affect cacheability, but it does affect the content of the response, so it has to be part of the ETag.
            eTagBuilder.with(ETagKeys.QUERY_PARAM_RETURN_ARTIFACT_TYPE, returnArtifactType);
        }
    }

    @Override
    public String description() {
        return getClass().getCanonicalName();
    }
}
