package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.extensions.ArtifactVersionWriteHook;
import io.apicurio.registry.extensions.VersionWriteContext;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/**
 * Test-only {@link ArtifactVersionWriteHook} used by {@link WriteHookReferencesRulesTest}: for groups
 * starting with {@link #GROUP_PREFIX}, it maps the {@code money.json} reference in the content to
 * {@code <group>/money@1}, the way an embedded-schema extraction hook adds the references it creates.
 */
@ApplicationScoped
public class ReferenceAddingWriteHook implements ArtifactVersionWriteHook {

    static final String GROUP_PREFIX = "ref-hook-test-";
    static final String REFERENCE_NAME = "money.json";

    @Override
    public ContentWrapperDto prepareContent(VersionWriteContext context, TypedContent content) {
        String groupId = context.getGa().getRawGroupIdWithNull();
        if (groupId == null || !groupId.startsWith(GROUP_PREFIX)
                || !content.getContent().content().contains(REFERENCE_NAME)) {
            return null;
        }
        return ContentWrapperDto.builder().content(content.getContent()).contentType(content.getContentType())
                .references(List.of(ArtifactReferenceDto.builder().groupId(groupId).artifactId("money")
                        .version("1").name(REFERENCE_NAME).build()))
                .build();
    }
}
