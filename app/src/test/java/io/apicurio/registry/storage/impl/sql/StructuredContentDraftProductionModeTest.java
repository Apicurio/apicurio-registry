package io.apicurio.registry.storage.impl.sql;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.EditableVersionMetaDataDto;
import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.types.VersionState;
import io.apicurio.registry.utils.tests.DraftProductionModeProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Draft production mode gives drafts real content hashes, so a draft can share its content row with a
 * published version. The draft must still stay out of the structured-content index, and removing it
 * must not drop the published version's rows.
 */
@QuarkusTest
@TestProfile(DraftProductionModeProfile.class)
public class StructuredContentDraftProductionModeTest {

    @Inject
    @Current
    RegistryStorage storage;

    @Test
    public void draftSharingPublishedContentDoesNotChangeIndex() {
        String group = "structure-dpm-" + UUID.randomUUID();
        ArtifactVersionMetaDataDto published = storage.createArtifact(group, "agent", "AGENT_CARD",
                EditableArtifactMetaDataDto.builder().build(), "1", content("shared"),
                EditableVersionMetaDataDto.builder().build(), List.of(), false, false, "test").getRight();
        ArtifactVersionMetaDataDto draft = storage.createArtifactVersion(group, "agent", "2", "AGENT_CARD",
                content("shared"), EditableVersionMetaDataDto.builder().build(), List.of(), true, false, "test");

        // Proves the mode is active: the draft reuses the published content row.
        assertEquals(published.getContentId(), draft.getContentId());
        assertEquals(VersionState.DRAFT, draft.getState());
        assertEquals(Set.of("agent"), matches(group, "skill:shared"));

        storage.createArtifactVersion(group, "agent", "3", "AGENT_CARD", content("draft-only"),
                EditableVersionMetaDataDto.builder().build(), List.of(), true, false, "test");
        assertEquals(Set.of(), matches(group, "skill:draft-only"));

        storage.deleteArtifactVersion(group, "agent", "2");
        assertEquals(Set.of("agent"), matches(group, "skill:shared"));
    }

    private ContentWrapperDto content(String skill) {
        return ContentWrapperDto.builder().content(ContentHandle.create("""
                {"name":"Agent","description":"Agent","version":"1","capabilities":{"streaming":true},
                 "skills":[{"id":"%s","name":"Skill","description":"Skill","tags":["test"]}],
                 "defaultInputModes":["text"],"defaultOutputModes":["text"]}
                """.formatted(skill))).contentType("application/json").references(List.of()).build();
    }

    private Set<String> matches(String group, String structure) {
        Set<SearchFilter> filters = new HashSet<>();
        filters.add(SearchFilter.ofGroupId(group));
        filters.add(SearchFilter.ofArtifactType("AGENT_CARD"));
        filters.add(SearchFilter.ofStructure(structure));
        Set<String> ids = new HashSet<>();
        storage.searchArtifacts(filters, OrderBy.artifactId, OrderDirection.asc, 0, 100, false).getArtifacts()
                .forEach(a -> ids.add(a.getArtifactId()));
        return ids;
    }
}
