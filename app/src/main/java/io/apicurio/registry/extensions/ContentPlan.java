package io.apicurio.registry.extensions;

import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;

import java.util.Map;

/**
 * The outcome of {@link ArtifactVersionWriteHook#planContent}: content rewritten without writing
 * anything, plus how to commit whatever the rewrite depends on.
 *
 * <p>
 * A hook that needs to register related artifacts or versions (for example, embedded schema
 * extraction) must not write them here: rules and other hooks' {@code beforePublish} can still
 * reject the request, and nothing would undo the write. Instead, the hook predicts the registration
 * (what it would create, and under which coordinates) and defers the actual write to
 * {@link #commit()}, run only after every check has passed and only when the request is not a dry
 * run.
 * </p>
 *
 * @param content                  the replacement content and type, with {@code references}
 *                                  holding only the references the rewrite introduced, exactly
 *                                  as {@link ArtifactVersionWriteHook#prepareContent} returns
 * @param plannedReferencedContent content for references introduced by {@code content} that are
 *                                  not yet persisted (keyed by {@code groupId}/{@code artifactId}/
 *                                  {@code version}, with {@code name} left unset), so reference
 *                                  resolution and rules see it before {@link #commit} runs
 * @param commit                   registers whatever {@code content} refers to that is not yet
 *                                  persisted; must throw if a predicted coordinate was taken by a
 *                                  concurrent write, rather than silently writing somewhere else
 */
public record ContentPlan(ContentWrapperDto content, Map<ArtifactReferenceDto, ContentWrapperDto> plannedReferencedContent,
        Runnable commit) {

    public ContentPlan {
        plannedReferencedContent = Map.copyOf(plannedReferencedContent);
    }
}
