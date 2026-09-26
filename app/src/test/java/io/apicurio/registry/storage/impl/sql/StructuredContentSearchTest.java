package io.apicurio.registry.storage.impl.sql;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.EditableVersionMetaDataDto;
import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.types.VersionState;
import io.apicurio.registry.utils.impexp.v3.ArtifactVersionEntity;
import io.apicurio.registry.utils.impexp.v3.BranchEntity;
import io.apicurio.registry.model.GA;
import io.apicurio.registry.model.BranchId;
import io.apicurio.registry.model.VersionId;
import io.apicurio.registry.storage.impl.sql.upgrader.StructuredContentUpgrader;
import io.apicurio.registry.storage.impl.sql.jdb.RuntimeSqlException;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static io.apicurio.registry.storage.impl.sql.StructuredContentIndexUtils.elementValue;

@QuarkusTest
public class StructuredContentSearchTest {
    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    HandleFactory handles;

    protected String group() { return "structure-" + UUID.randomUUID(); }

    protected ContentWrapperDto content(String skill) {
        return ContentWrapperDto.builder().content(ContentHandle.create("""
                {"name":"Agent","description":"Agent","version":"1","capabilities":{"streaming":true},
                 "skills":[{"id":"%s","name":"Skill","description":"Skill","tags":["test"]}],
                 "defaultInputModes":["text"],"defaultOutputModes":["text"]}
                """.formatted(skill))).contentType("application/json").references(List.of()).build();
    }

    protected void create(String group, String artifact, String skill) {
        storage.createArtifact(group,artifact,"AGENT_CARD",EditableArtifactMetaDataDto.builder().build(),"1",
                content(skill),EditableVersionMetaDataDto.builder().build(),List.of(),false,false,"test");
    }

    protected Set<String> matches(String group, SearchFilter filter) {
        Set<SearchFilter> filters = new HashSet<>();
        filters.add(SearchFilter.ofGroupId(group));
        filters.add(SearchFilter.ofArtifactType("AGENT_CARD"));
        filters.add(filter);
        var result = storage.searchArtifacts(filters,OrderBy.artifactId,OrderDirection.asc,0,100,false);
        Set<String> ids = new HashSet<>();
        result.getArtifacts().forEach(a -> ids.add(a.getArtifactId()));
        assertEquals(ids.size(),result.getCount());
        return ids;
    }

    @Test
    public void filtersMatchExactlyAndNegationIsScoped() {
        String group=group();
        create(group,"one","Weather");
        create(group,"two","other");
        for (String filter : List.of("agent_card:skill:weather","skill:WEATHER","weather")) {
            assertEquals(Set.of("one"),matches(group,SearchFilter.ofStructure(filter)));
        }
        assertEquals(Set.of("two"),matches(group,SearchFilter.ofStructure("skill:weather").negated()));
        assertEquals(Set.of(),matches(group,SearchFilter.ofStructure("sk_ll:weather")));
        assertEquals(Set.of(),matches(group,SearchFilter.ofStructure("%:weather")));
        assertThrows(IllegalArgumentException.class, () -> storage.searchArtifacts(
                Set.of(SearchFilter.ofStructure("skill:weather").negated()),OrderBy.artifactId,OrderDirection.asc,0,10,false));
        for (String invalid : List.of("", " ", "skill:", ":weather", "agent_card::weather")) {
            assertThrows(IllegalArgumentException.class, () -> matches(group,SearchFilter.ofStructure(invalid)));
        }
    }

    @Test
    public void draftsStateChangesAndDeletionKeepPublishedContentIndexed() {
        String group=group();
        create(group,"agent","published");
        storage.createArtifactVersion(group,"agent","2","AGENT_CARD",content("draft"),
                EditableVersionMetaDataDto.builder().build(),List.of(),true,false,"test");
        assertEquals(Set.of("agent"),matches(group,SearchFilter.ofStructure("skill:published")));
        assertEquals(Set.of(),matches(group,SearchFilter.ofStructure("skill:draft")));
        storage.updateArtifactVersionContent(group,"agent","2","AGENT_CARD",content("edited"));
        assertEquals(Set.of(),matches(group,SearchFilter.ofStructure("skill:edited")));
        storage.updateArtifactVersionState(group,"agent","2",VersionState.ENABLED,false);
        assertEquals(Set.of("agent"),matches(group,SearchFilter.ofStructure("skill:edited")));
        assertEquals(Set.of(),matches(group,SearchFilter.ofStructure("skill:published")));
        storage.updateArtifactVersionState(group,"agent","2",VersionState.DISABLED,false);
        assertEquals(Set.of("agent"),matches(group,SearchFilter.ofStructure("skill:published")));
        storage.updateArtifactVersionState(group,"agent","2",VersionState.ENABLED,false);
        storage.deleteArtifactVersion(group,"agent","2");
        assertEquals(Set.of("agent"),matches(group,SearchFilter.ofStructure("skill:published")));
        storage.deleteArtifactVersion(group,"agent","1");
        assertEquals(Set.of(),matches(group,SearchFilter.ofStructure("skill:published")));
    }

    @Test
    public void importedVersionsAreSearchableWithoutReupload() {
        String group=group();
        create(group,"source","imported");
        var source=storage.getArtifactVersionMetaData(group,"source","1");
        storage.createArtifact(group,"target","AGENT_CARD",EditableArtifactMetaDataDto.builder().build(),null,
                null,null,List.of(),false,false,"test");
        ArtifactVersionEntity version=new ArtifactVersionEntity();
        version.groupId=group;
        version.artifactId="target";
        version.version="1";
        version.versionOrder=1;
        version.globalId=storage.nextGlobalId();
        version.contentId=source.getContentId();
        version.state=VersionState.ENABLED;
        version.createdOn=source.getCreatedOn();
        version.modifiedOn=source.getModifiedOn();
        storage.importArtifactVersion(version);
        assertEquals(Set.of("source","target"),matches(group,SearchFilter.ofStructure("skill:imported")));
    }

    @Test
    public void backfillRebuildsPublishedTipAndCanBeRepeated() {
        String group=group();
        create(group,"agent","published");
        storage.createArtifactVersion(group,"agent","2","AGENT_CARD",content("draft"),
                EditableVersionMetaDataDto.builder().build(),List.of(),true,false,"test");
        handles.withHandleNoException(handle -> {
            handle.createUpdate("DELETE FROM artifact_structured_content WHERE groupId = ?")
                    .bind(0,group).execute();
            new StructuredContentUpgrader().upgrade(handle);
            new StructuredContentUpgrader().upgrade(handle);
            return null;
        });
        assertEquals(Set.of("agent"),matches(group,SearchFilter.ofStructure("skill:published")));
        assertEquals(Set.of(),matches(group,SearchFilter.ofStructure("skill:draft")));
    }

    @Test
    public void publicationOrderRatherThanVersionOrderSelectsIndexedTip() {
        String group=group();
        storage.createArtifact(group,"agent","AGENT_CARD",EditableArtifactMetaDataDto.builder().build(),"1",
                content("earlier-draft"),EditableVersionMetaDataDto.builder().build(),List.of(),true,false,"test");
        storage.createArtifactVersion(group,"agent","2","AGENT_CARD",content("later-version"),
                EditableVersionMetaDataDto.builder().build(),List.of(),false,false,"test");
        storage.updateArtifactVersionState(group,"agent","1",VersionState.ENABLED,false);
        assertEquals(Set.of("agent"),matches(group,SearchFilter.ofStructure("skill:earlier-draft")));
        assertEquals(Set.of(),matches(group,SearchFilter.ofStructure("skill:later-version")));
        handles.withHandleNoException(handle -> {
            new StructuredContentUpgrader().upgrade(handle);
            return null;
        });
        assertEquals(Set.of("agent"),matches(group,SearchFilter.ofStructure("skill:earlier-draft")));
        assertEquals(Set.of(),matches(group,SearchFilter.ofStructure("skill:later-version")));
    }

    @Test
    public void importedEmptyLatestBranchIsIndexedWhenVersionsAreAppended() {
        String group=group();
        create(group,"source","imported");
        var source=storage.getArtifactVersionMetaData(group,"source","1");
        storage.createArtifact(group,"target","AGENT_CARD",EditableArtifactMetaDataDto.builder().build(),null,
                null,null,List.of(),false,false,"test");
        storage.importBranch(BranchEntity.builder().groupId(group).artifactId("target").branchId("latest")
                .systemDefined(false).createdOn(source.getCreatedOn()).modifiedOn(source.getModifiedOn()).build());
        ArtifactVersionEntity version=new ArtifactVersionEntity();
        version.groupId=group;
        version.artifactId="target";
        version.version="1";
        version.versionOrder=1;
        version.globalId=storage.nextGlobalId();
        version.contentId=source.getContentId();
        version.state=VersionState.ENABLED;
        version.createdOn=source.getCreatedOn();
        version.modifiedOn=source.getModifiedOn();
        storage.importArtifactVersion(version);
        assertEquals(Set.of("source"),matches(group,SearchFilter.ofStructure("skill:imported")));
        storage.appendVersionToBranch(new GA(group,"target"),BranchId.LATEST,new VersionId("1"));
        assertEquals(Set.of("source","target"),matches(group,SearchFilter.ofStructure("skill:imported")));
        storage.replaceBranchVersions(new GA(group,"target"),BranchId.LATEST,List.of());
        assertEquals(Set.of("source"),matches(group,SearchFilter.ofStructure("skill:imported")));
        storage.deleteBranch(new GA(group,"target"),BranchId.LATEST);
        assertEquals(Set.of("source","target"),matches(group,SearchFilter.ofStructure("skill:imported")));
    }

    @Test
    public void publishingDuplicateDraftContentKeepsTransactionUsable() {
        String group=group();
        create(group,"agent","shared");
        storage.createArtifactVersion(group,"agent","2","AGENT_CARD",content("shared"),
                EditableVersionMetaDataDto.builder().build(),List.of(),true,false,"test");
        storage.updateArtifactVersionState(group,"agent","2",VersionState.ENABLED,false);
        assertEquals(VersionState.ENABLED,storage.getArtifactVersionState(group,"agent","2"));
        assertEquals(Set.of("agent"),matches(group,SearchFilter.ofStructure("skill:shared")));
    }

    @Test
    public void accentDistinctValuesDoNotCollideDuringWriteOrBackfill() {
        String group=group();
        String json=content("cafe").getContent().content().replace("\"tags\":[\"test\"]", "\"tags\":[\"cafe\",\"café\"]");
        storage.createArtifact(group,"agent","AGENT_CARD",EditableArtifactMetaDataDto.builder().build(),"1",
                ContentWrapperDto.builder().content(ContentHandle.create(json)).contentType("application/json")
                        .references(List.of()).build(),EditableVersionMetaDataDto.builder().build(),List.of(),false,false,"test");
        assertEquals(Set.of("agent"),matches(group,SearchFilter.ofStructure("tag:cafe")));
        assertEquals(Set.of("agent"),matches(group,SearchFilter.ofStructure("tag:café")));
        handles.withHandleNoException(handle -> {
            new StructuredContentUpgrader().upgrade(handle);
            return null;
        });
        assertEquals(Set.of("agent"),matches(group,SearchFilter.ofStructure("tag:café")));
    }

    @Test
    public void fullLengthValuesRemainDistinctThroughAllQueryFormsAndBackfill() {
        String group = group();
        String prefix = "shared-" + "x".repeat(300);
        String first = prefix + "-one";
        String second = prefix + "-two";
        create(group, "one", first);
        create(group, "two", second);
        create(group, "literal-digest", elementValue(first));
        for (int pass = 0; pass < 2; pass++) {
            for (String form : List.of("agent_card:skill:", "skill:", "")) {
                assertEquals(Set.of("one"), matches(group, SearchFilter.ofStructure(form + first)));
                assertEquals(Set.of("two"), matches(group, SearchFilter.ofStructure(form + second)));
                assertEquals(Set.of("literal-digest"), matches(group, SearchFilter.ofStructure(form + elementValue(first))));
            }
            if (pass == 0) {
                handles.withHandleNoException(handle -> {
                    new StructuredContentUpgrader().upgrade(handle);
                    return null;
                });
            }
        }
    }

    @Test
    public void databaseIndexFailureRollsBackVersionAndPreviousIndex() {
        String group=group();
        create(group,"agent","before");
        String blocked="blocked" + UUID.randomUUID().toString().replace("-", "");
        String constraint="structure_" + UUID.randomUUID().toString().replace("-", "");
        handles.withHandleNoException(handle -> {
            handle.createUpdate("ALTER TABLE artifact_structured_content ADD CONSTRAINT " + constraint
                    + " CHECK (elementValue <> '" + elementValue(blocked) + "')").execute();
            return null;
        });
        try {
            var failure = assertThrows(RuntimeSqlException.class, () -> storage.createArtifactVersion(group,"agent","2",
                    "AGENT_CARD",content(blocked),EditableVersionMetaDataDto.builder().build(),List.of(),false,false,"test"));
            assertTrue(failure.toString().toLowerCase(Locale.ROOT).contains(constraint));
            assertEquals(List.of("1"),storage.getArtifactVersions(group,"agent"));
            assertEquals(Set.of("agent"),matches(group,SearchFilter.ofStructure("skill:before")));
            assertEquals(Set.of(),matches(group,SearchFilter.ofStructure("skill:" + blocked)));
        } finally {
            handles.withHandleNoException(handle -> {
                handle.createUpdate("ALTER TABLE artifact_structured_content DROP CONSTRAINT " + constraint).execute();
                return null;
            });
        }
        storage.createArtifactVersion(group,"agent","2","AGENT_CARD",content(blocked),
                EditableVersionMetaDataDto.builder().build(),List.of(),false,false,"test");
        assertEquals(Set.of("agent"),matches(group,SearchFilter.ofStructure("skill:" + blocked)));
    }
}
