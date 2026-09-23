package io.apicurio.registry.extensions;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;

import java.util.List;

/**
 * Content rewritten by {@link ArtifactVersionWriteHook#prepareContent}, together with any references the
 * rewrite introduced. The references are added to the ones submitted with the version.
 */
public final class PreparedContent {

    private final ContentHandle content;
    private final String contentType;
    private final List<ArtifactReferenceDto> addedReferences;

    public PreparedContent(ContentHandle content, String contentType, List<ArtifactReferenceDto> addedReferences) {
        this.content = content;
        this.contentType = contentType;
        this.addedReferences = List.copyOf(addedReferences);
    }

    public ContentHandle getContent() {
        return content;
    }

    public String getContentType() {
        return contentType;
    }

    public List<ArtifactReferenceDto> getAddedReferences() {
        return addedReferences;
    }
}
