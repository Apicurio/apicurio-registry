package io.apicurio.registry.extensions;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.storage.dto.ContentWrapperDto;

/**
 * Extension point that lets an optional module take part in artifact version writes made through the
 * v3 REST API, without the core REST layer knowing about the module's artifact types.
 * <p>
 * Implementations are CDI beans; every bean implementing this interface is invoked for every write, so
 * each implementation must check {@link VersionWriteContext#getArtifactType()} and ignore types it
 * does not handle. Both stages are optional.
 * </p>
 */
public interface ArtifactVersionWriteHook {

    /**
     * Stage 1: rewrite the submitted content before rules are applied and before it is stored.
     * <p>
     * Called when an artifact is created with a first version and when a new version is created.
     * Drafts are included.
     * </p>
     *
     * @param context the write being performed
     * @param content the content as submitted, or as rewritten by a previous hook
     * @return the replacement content and type, with {@code references} holding only the references the
     *         rewrite introduced (they are added to those submitted with the version), or {@code null} to
     *         leave the content unchanged
     */
    default ContentWrapperDto prepareContent(VersionWriteContext context, TypedContent content) {
        return null;
    }

    /**
     * Stage 2: inspect content that is about to be published, after rules have passed and before the
     * version is persisted. Not called for draft versions.
     * <p>
     * Throwing rejects the write, just like a rule violation. The returned action, if any, runs only
     * after the version was persisted and only when the request is not a dry run.
     * </p>
     *
     * @param context the write being performed
     * @param content the content that will be published
     * @return an action to run after a successful publish, or {@code null}
     */
    default Runnable beforePublish(VersionWriteContext context, TypedContent content) {
        return null;
    }
}
