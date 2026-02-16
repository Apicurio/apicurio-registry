package io.apicurio.registry.rest.cache.strategy;

import io.apicurio.registry.model.VersionId;
import io.apicurio.registry.rest.cache.etag.ETagBuilder;
import io.apicurio.registry.rest.cache.etag.ETagKeys;
import io.apicurio.registry.rest.v3.beans.HandleReferencesType;
import io.apicurio.registry.types.VersionState;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;
import java.util.function.Supplier;

import static io.apicurio.registry.rest.cache.Cacheability.HIGH;
import static io.apicurio.registry.rest.cache.Cacheability.LOW;
import static io.apicurio.registry.rest.cache.Cacheability.MODERATE;
import static io.apicurio.registry.rest.cache.Cacheability.min;
import static io.apicurio.registry.rest.v3.beans.HandleReferencesType.PRESERVE;
import static io.apicurio.registry.types.VersionState.DRAFT;
import static lombok.AccessLevel.PRIVATE;

@AllArgsConstructor(access = PRIVATE)
@Builder
public class VersionContentCacheStrategy extends CacheStrategy {

    // ETag
    private final long contentId;
    private final HandleReferencesType references;
    /**
     * Etag has to change if any references are in a DRAFT state and are being updated.
     * It's more expensive to compute, so we do so on demand.
     */
    private final Supplier<List<Long>> referenceTreeContentIds;
    // Cacheability
    private final String versionExpression;
    private final VersionState versionState;
    // IMPORTANT: Any of the parameters can be null. Be careful about default values.

    @Override
    public void evaluate() {

        eTagBuilder = new ETagBuilder()
                .with(ETagKeys.CONTENT_ID, contentId)
                .with(ETagKeys.QUERY_PARAM_REFERENCES, references);

        cacheability = HIGH;

        if (!VersionId.isValid(versionExpression) /* is a version expression */) {
            cacheability = min(cacheability, MODERATE);
        }
        if (DRAFT.equals(versionState) && isVersionMutabilityEnabled()) {
            cacheability = min(cacheability, MODERATE);
        }
        if (references != null && !PRESERVE.equals(references) && isVersionMutabilityEnabled()) {
            if (referenceTreeContentIds != null && isHigherQualityEtagEnabled()) {
                eTagBuilder.with(ETagKeys.REFERENCE_TREE_CONTENT_IDS, referenceTreeContentIds.get());
                cacheability = min(cacheability, MODERATE);
            } else {
                eTagBuilder.withRandom();
                cacheability = min(cacheability, LOW);
            }
        }
    }

    @Override
    public String description() {
        return getClass().getCanonicalName();
    }
}
