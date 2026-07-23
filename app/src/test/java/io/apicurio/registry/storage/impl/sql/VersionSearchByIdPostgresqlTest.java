package io.apicurio.registry.storage.impl.sql;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.rest.client.models.CreateArtifactResponse;
import io.apicurio.registry.rest.client.models.VersionSearchResults;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.dto.VersionSearchResultsDto;
import io.apicurio.registry.storage.util.PostgresqlTestProfile;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Set;

@QuarkusTest
@Tag(ApicurioTestTags.SLOW)
@TestProfile(PostgresqlTestProfile.class)
public class VersionSearchByIdPostgresqlTest extends AbstractResourceTestBase {

    @Inject
    @Current
    RegistryStorage storage;

    @Test
    void testSearchVersionsByGlobalIdOnPostgresql() throws Exception {
        String groupId = TestUtils.generateGroupId();

        CreateArtifactResponse car1 = createArtifact(groupId, "global-id-artifact-1", ArtifactType.JSON,
                "{}", ContentTypes.APPLICATION_JSON);
        CreateArtifactResponse car2 = createArtifact(groupId, "global-id-artifact-2", ArtifactType.JSON,
                "{\"type\":\"string\"}", ContentTypes.APPLICATION_JSON);

        VersionSearchResults results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = groupId;
            config.queryParameters.globalId = car1.getVersion().getGlobalId();
        });
        Assertions.assertNotNull(results);
        Assertions.assertEquals(1, results.getCount());
        Assertions.assertEquals(car1.getVersion().getGlobalId(),
                results.getVersions().get(0).getGlobalId());

        TestUtils.retry(() -> {
            VersionSearchResultsDto negated = storage.searchVersions(
                    Set.of(SearchFilter.ofGroupId(groupId),
                            SearchFilter.ofGlobalId(car1.getVersion().getGlobalId()).negated()),
                    OrderBy.globalId, OrderDirection.asc, 0, 10, false);
            Assertions.assertNotNull(negated);
            Assertions.assertEquals(1, negated.getCount());
            Assertions.assertEquals(car2.getVersion().getGlobalId(),
                    negated.getVersions().get(0).getGlobalId());
        });
    }

    @Test
    void testSearchVersionsByContentIdOnPostgresql() throws Exception {
        String groupId = TestUtils.generateGroupId();

        CreateArtifactResponse car1 = createArtifact(groupId, "content-id-artifact-1", ArtifactType.JSON,
                "{}", ContentTypes.APPLICATION_JSON);
        CreateArtifactResponse car2 = createArtifact(groupId, "content-id-artifact-2", ArtifactType.JSON,
                "{\"type\":\"string\"}", ContentTypes.APPLICATION_JSON);

        VersionSearchResults results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = groupId;
            config.queryParameters.contentId = car1.getVersion().getContentId();
        });
        Assertions.assertNotNull(results);
        Assertions.assertEquals(1, results.getCount());
        Assertions.assertEquals(car1.getVersion().getContentId(),
                results.getVersions().get(0).getContentId());

        TestUtils.retry(() -> {
            VersionSearchResultsDto negated = storage.searchVersions(
                    Set.of(SearchFilter.ofGroupId(groupId),
                            SearchFilter.ofContentId(car1.getVersion().getContentId()).negated()),
                    OrderBy.globalId, OrderDirection.asc, 0, 10, false);
            Assertions.assertNotNull(negated);
            Assertions.assertEquals(1, negated.getCount());
            Assertions.assertEquals("content-id-artifact-2",
                    negated.getVersions().get(0).getArtifactId());
            Assertions.assertEquals(car2.getVersion().getContentId(),
                    negated.getVersions().get(0).getContentId());
        });
    }
}
