package io.apicurio.registry.auth.grants;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.ArtifactSearchResults;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.SearchedArtifact;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end test of per-resource authorization with the grants-based Kroxylicious Authorizer
 * enabled. Covers point access (allowed and 403) and search pre-filtering.
 */
@QuarkusTest
@TestProfile(GrantsAuthorizationTest.GrantsTestProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class GrantsAuthorizationTest extends AbstractResourceTestBase {

    private static final String CONTENT = "{\"type\":\"object\"}";
    private static final String TEAM_A = "grants-team-a";
    private static final String TEAM_B = "grants-team-b";

    public static class GrantsTestProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            Map<String, String> map = new HashMap<>();
            map.put("quarkus.oidc.tenant-enabled", "false");
            map.put("quarkus.http.auth.basic", "true");
            map.put("apicurio.auth.role-based-authorization", "true");
            map.put("quarkus.security.users.embedded.enabled", "true");
            map.put("quarkus.security.users.embedded.plain-text", "true");
            map.put("quarkus.security.users.embedded.users.alice", "alice");
            map.put("quarkus.security.users.embedded.users.bob1", "bob1");
            map.put("quarkus.security.users.embedded.users.bob2", "bob2");
            map.put("quarkus.security.users.embedded.roles.alice", "sr-admin");
            map.put("quarkus.security.users.embedded.roles.bob1", "sr-developer");
            map.put("quarkus.security.users.embedded.roles.bob2", "sr-developer");
            map.put("apicurio.features.experimental.enabled", "true");
            map.put("apicurio.auth.resource-based-authorization.enabled", "true");
            map.put("apicurio.auth.resource-based-authorization.grants.path", grantsFilePath());
            map.put("apicurio.auth.resource-based-authorization.grants.reload-enabled", "false");
            return map;
        }

        private static String grantsFilePath() {
            try {
                return Path.of(GrantsAuthorizationTest.class.getClassLoader()
                        .getResource("grants/grants-auth-test.json").toURI()).toString();
            } catch (URISyntaxException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @Override
    protected RegistryClient createRestClientV3(Vertx vertx) {
        return clientFor("alice");
    }

    private RegistryClient clientFor(String user) {
        // Test users use their username as password
        return RegistryClientFactory.create(RegistryClientOptions.create()
                .registryUrl(registryV3ApiUrl)
                .vertx(vertx)
                .basicAuth(user, user));
    }

    private String createAsAdmin(String groupId, String artifactIdPrefix) throws Exception {
        String artifactId = artifactIdPrefix + TestUtils.generateArtifactId();
        CreateArtifact create = TestUtils.clientCreateArtifact(artifactId, ArtifactType.JSON, CONTENT,
                ContentTypes.APPLICATION_JSON);
        clientV3.groups().byGroupId(groupId).artifacts().post(create);
        return artifactId;
    }

    private static Set<String> artifactIds(ArtifactSearchResults results) {
        return results.getArtifacts().stream().map(SearchedArtifact::getArtifactId)
                .collect(Collectors.toSet());
    }

    @Test
    public void userWithPrefixGrantCanReadArtifact() throws Exception {
        String artifactId = createAsAdmin(TEAM_A, "a-");

        var meta = clientFor("bob1").groups().byGroupId(TEAM_A).artifacts().byArtifactId(artifactId).get();

        assertEquals(artifactId, meta.getArtifactId());
        assertEquals(TEAM_A, meta.getGroupId());
    }

    @Test
    public void userWithoutMatchingGrantIsForbidden() throws Exception {
        String artifactId = createAsAdmin(TEAM_B, "hidden-");

        var exception = assertThrows(Exception.class, () -> clientFor("bob1").groups().byGroupId(TEAM_B)
                .artifacts().byArtifactId(artifactId).get());

        assertForbidden(exception);
    }

    @Test
    public void userWithNoGrantsForGroupIsForbidden() throws Exception {
        String artifactId = createAsAdmin(TEAM_A, "a-");

        var exception = assertThrows(Exception.class, () -> clientFor("bob2").groups().byGroupId(TEAM_A)
                .artifacts().byArtifactId(artifactId).get());

        assertForbidden(exception);
    }

    @Test
    public void subPathPrefixGrantAllowsOnlyMatchingArtifacts() throws Exception {
        String visible = createAsAdmin(TEAM_B, "visible-");
        String hidden = createAsAdmin(TEAM_B, "hidden-");
        RegistryClient bob2 = clientFor("bob2");

        assertEquals(visible, bob2.groups().byGroupId(TEAM_B).artifacts().byArtifactId(visible).get()
                .getArtifactId());
        var exception = assertThrows(Exception.class,
                () -> bob2.groups().byGroupId(TEAM_B).artifacts().byArtifactId(hidden).get());
        assertForbidden(exception);
    }

    @Test
    public void searchOnlyReturnsArtifactsMatchingSubPathPrefixGrant() throws Exception {
        String visible = createAsAdmin(TEAM_B, "visible-");
        String hidden = createAsAdmin(TEAM_B, "hidden-");

        Set<String> found = artifactIds(clientFor("bob2").search().artifacts().get(config -> {
            config.queryParameters.groupId = TEAM_B;
            config.queryParameters.limit = 1000;
        }));

        // A "grants-team-b/visible-" grant must not be widened to the whole group in search.
        assertTrue(found.contains(visible), "visible artifact missing from " + found);
        assertFalse(found.contains(hidden), "hidden artifact leaked into " + found);
        found.forEach(id -> assertTrue(id.startsWith("visible-"), "unexpected artifact " + id));
    }

    @Test
    public void searchExcludesGroupsWithoutGrants() throws Exception {
        String teamA = createAsAdmin(TEAM_A, "a-");
        String teamB = createAsAdmin(TEAM_B, "visible-");

        Set<String> found = artifactIds(clientFor("bob1").search().artifacts().get(config -> {
            config.queryParameters.limit = 1000;
        }));

        assertTrue(found.contains(teamA), "team-a artifact missing from " + found);
        assertFalse(found.contains(teamB), "team-b artifact leaked into " + found);
    }

    @Test
    public void adminRoleBypassesGrants() throws Exception {
        String artifactId = createAsAdmin(TEAM_B, "hidden-");

        var meta = clientV3.groups().byGroupId(TEAM_B).artifacts().byArtifactId(artifactId).get();

        assertEquals(artifactId, meta.getArtifactId());
    }
}
