package io.apicurio.registry.references;

import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * A reference loader that authorizes what it loads, and can authorize many references at once.
 * <p>
 * {@link #apply} authorizes the reference it loads, unless it was already authorized through
 * {@link #authorize}, so it is a safe drop-in wherever a plain loader is expected. Reference walks that
 * know several references before loading them (a level of the graph, the siblings in a document) call
 * {@link #authorizeAll} first, so that the whole set is authorized with as few authorizer calls as
 * possible, which lets remote authorizers batch them.
 */
public interface AuthorizingReferenceLoader extends Function<ArtifactReferenceDto, ContentWrapperDto> {

    /**
     * Authorizes every reference in {@code references}, in as few authorizer calls as possible.
     *
     * Throws a {@code ForbiddenException} (403) if the caller may not read any of them.
     */
    void authorize(Collection<ArtifactReferenceDto> references);

    /**
     * Authorizes {@code references} at once if {@code loader} is an {@link AuthorizingReferenceLoader};
     * otherwise does nothing (the loader does not authorize).
     */
    static void authorizeAll(Function<ArtifactReferenceDto, ?> loader, Collection<ArtifactReferenceDto> references) {
        if (loader instanceof AuthorizingReferenceLoader authorizing && !references.isEmpty()) {
            authorizing.authorize(references);
        }
    }

    /**
     * @return a loader returning {@code planned} content (planned, but not yet persisted) in preference
     *         to loading it with {@code fallback}. If {@code fallback} authorizes, so does the result:
     *         every reference, planned or not, is authorized as {@code fallback} would, and still in
     *         batches.
     */
    static Function<ArtifactReferenceDto, ContentWrapperDto> preferring(
            Map<ArtifactReferenceDto, ContentWrapperDto> planned,
            Function<ArtifactReferenceDto, ContentWrapperDto> fallback) {
        if (fallback instanceof AuthorizingReferenceLoader authorizing) {
            return new AuthorizingReferenceLoader() {
                @Override
                public void authorize(Collection<ArtifactReferenceDto> references) {
                    authorizing.authorize(references);
                }

                @Override
                public ContentWrapperDto apply(ArtifactReferenceDto reference) {
                    authorizing.authorize(List.of(reference));
                    ContentWrapperDto content = planned.get(reference);
                    return content != null ? content : authorizing.apply(reference);
                }
            };
        }
        return reference -> {
            ContentWrapperDto content = planned.get(reference);
            return content != null ? content : fallback.apply(reference);
        };
    }
}
