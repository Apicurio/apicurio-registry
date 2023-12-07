package io.apicurio.registry.storage.dto;

import io.apicurio.registry.content.ContentHandle;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.List;

@AllArgsConstructor
@Builder
@EqualsAndHashCode
@ToString
public class ContentWrapperDto {

    private ContentHandle content;
    private List<ArtifactReferenceDto> references;

    public ContentWrapperDto() {
    }

    /**
     * @return the content handle
     */
    public ContentHandle getContent() {
        return content;
    }

    /**
     * @param content
     */
    public void setContent(ContentHandle content) {
        this.content = content;
    }

    /**
     * @return the content references
     */
    public List<ArtifactReferenceDto> getReferences() {
        return references;
    }

    /**
     * @param references
     */
    public void setReferences(List<ArtifactReferenceDto> references) {
        this.references = references;
    }
}
