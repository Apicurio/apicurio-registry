package io.apicurio.authz;

import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrantsAuthorizerTest {

    enum Artifact implements ResourceType<Artifact> {
        Read, Write, Admin;
        @Override
        public Set<Artifact> implies() {
            return switch (this) {
                case Admin -> Set.of(Write, Read);
                case Write -> Set.of(Read);
                case Read -> Set.of();
            };
        }
    }

    enum Group implements ResourceType<Group> { Read, Write, Admin }
    enum Topic implements ResourceType<Topic> { Read, Write, Create, Delete }
    enum Dashboard implements ResourceType<Dashboard> { Read, Write }

    private static GrantsAuthorizer authorizer;

    @BeforeAll
    static void setUp() throws Exception {
        URL grantsUrl = GrantsAuthorizerTest.class.getClassLoader().getResource("test-grants.json");
        assertNotNull(grantsUrl);
        authorizer = GrantsAuthorizer.create(Path.of(grantsUrl.toURI()),
                Map.of(
                        Artifact.class, "artifact",
                        Group.class, "group",
                        Topic.class, "topic",
                        Dashboard.class, "dashboard"
                ));
    }

    @AfterAll
    static void tearDown() {
        if (authorizer != null) {
            authorizer.close();
        }
    }

    private Subject user(String name, String... roles) {
        if (roles.length == 0) {
            return new Subject(new User(name));
        }
        var principals = new java.util.HashSet<io.apicurio.authz.Principal>();
        principals.add(new User(name));
        for (String role : roles) {
            principals.add(new RolePrincipal(role));
        }
        return new Subject(principals);
    }

    private Decision decide(Subject subject, ResourceType<?> op, String resourceName) {
        AuthorizeResult result = authorizer.authorize(subject, List.of(new Action(op, resourceName)))
                .toCompletableFuture().join();
        return result.decision(op, resourceName);
    }

    // ==================== Registry artifacts ====================

    @Test
    void developerCanReadOwnArtifact() {
        assertEquals(Decision.ALLOW, decide(user("developer-client"), Artifact.Read, "team-a/schema-1"));
    }

    @Test
    void developerCanWriteOwnArtifact() {
        assertEquals(Decision.ALLOW, decide(user("developer-client"), Artifact.Write, "team-a/schema-1"));
    }

    @Test
    void developerCanReadShared() {
        assertEquals(Decision.ALLOW, decide(user("developer-client"), Artifact.Read, "shared/common"));
    }

    @Test
    void developerCannotWriteShared() {
        assertEquals(Decision.DENY, decide(user("developer-client"), Artifact.Write, "shared/common"));
    }

    @Test
    void developerCannotReadTeamB() {
        assertEquals(Decision.DENY, decide(user("developer-client"), Artifact.Read, "team-b/secret"));
    }

    // ==================== Admin bypass ====================

    @Test
    void adminCanDoAnything() {
        Subject admin = user("superuser", "sr-admin");
        assertEquals(Decision.ALLOW, decide(admin, Artifact.Admin, "any-group/any-artifact"));
        assertEquals(Decision.ALLOW, decide(admin, Topic.Write, "any-topic"));
        assertEquals(Decision.ALLOW, decide(admin, Dashboard.Write, "any-dashboard"));
    }

    // ==================== Role-based grants ====================

    @Test
    void readonlyRoleCanReadShared() {
        Subject readonly = user("readonly-client", "sr-readonly");
        assertEquals(Decision.ALLOW, decide(readonly, Artifact.Read, "shared/common"));
    }

    @Test
    void readonlyRoleCannotReadTeamA() {
        Subject readonly = user("readonly-client", "sr-readonly");
        assertEquals(Decision.DENY, decide(readonly, Artifact.Read, "team-a/schema"));
    }

    @Test
    void developerRoleCanReadPublic() {
        Subject dev = user("anyone", "sr-developer");
        assertEquals(Decision.ALLOW, decide(dev, Artifact.Read, "public/common-schema"));
    }

    // ==================== Batched authorization ====================

    @Test
    void batchedAuthorizationWorks() {
        Subject dev = user("developer-client");
        List<Action> actions = List.of(
                new Action(Artifact.Read, "team-a/schema-1"),
                new Action(Artifact.Read, "team-a/schema-2"),
                new Action(Artifact.Read, "team-b/secret"),
                new Action(Artifact.Read, "shared/common")
        );

        AuthorizeResult result = authorizer.authorize(dev, actions).toCompletableFuture().join();

        assertEquals(3, result.allowed().size());
        assertEquals(1, result.denied().size());
        assertEquals("team-b/secret", result.denied().get(0).resourceName());
    }

    @Test
    void partitionWorksForSearchFiltering() {
        Subject dev = user("developer-client");
        List<String> searchResults = List.of(
                "team-a/schema-1", "team-a/schema-2",
                "team-b/secret", "shared/common"
        );

        List<Action> actions = searchResults.stream()
                .map(name -> new Action(Artifact.Read, name))
                .toList();

        AuthorizeResult result = authorizer.authorize(dev, actions).toCompletableFuture().join();
        Map<Decision, List<String>> partitioned = result.partition(
                searchResults, Artifact.Read, name -> name);

        assertEquals(3, partitioned.get(Decision.ALLOW).size());
        assertEquals(1, partitioned.get(Decision.DENY).size());
        assertTrue(partitioned.get(Decision.ALLOW).contains("team-a/schema-1"));
        assertTrue(partitioned.get(Decision.DENY).contains("team-b/secret"));
    }

    // ==================== Cross-system ====================

    @Test
    void sameFileMultipleResourceTypes() {
        Subject dev = user("developer-client");
        assertEquals(Decision.ALLOW, decide(dev, Artifact.Write, "team-a/schema"));
        assertEquals(Decision.DENY, decide(dev, Topic.Write, "team-a.events"));
    }

    // ==================== Unknown/anonymous ====================

    @Test
    void unknownUserDenied() {
        assertEquals(Decision.DENY, decide(user("unknown"), Artifact.Read, "team-a/x"));
    }

    @Test
    void anonymousDenied() {
        AuthorizeResult result = authorizer.authorize(Subject.anonymous(),
                List.of(new Action(Artifact.Read, "team-a/x")))
                .toCompletableFuture().join();
        assertEquals(Decision.DENY, result.decision(Artifact.Read, "team-a/x"));
    }

    // ==================== GrantsData ====================

    @Test
    void grantsDataMalformedJson() {
        GrantsData data = GrantsData.parse("broken{{{");
        assertFalse(data.isAdmin(Set.of("sr-admin")));
        assertTrue(data.getGrantsForUser("anyone", Set.of()).isEmpty());
    }

    // ==================== Deny rules ====================

    @Test
    void denyRuleBlocksAllowedResource() {
        Subject dev = user("developer-client");
        assertEquals(Decision.ALLOW, decide(dev, Artifact.Read, "team-a/schema-1"));
        assertEquals(Decision.DENY, decide(dev, Artifact.Read, "team-a/secret-schema"));
    }

    @Test
    void denyRuleDoesNotAffectOtherResources() {
        Subject dev = user("developer-client");
        assertEquals(Decision.ALLOW, decide(dev, Artifact.Write, "team-a/other-schema"));
        assertEquals(Decision.ALLOW, decide(dev, Artifact.Read, "shared/common"));
    }

    @Test
    void denyRuleTakesPrecedenceOverAllow() {
        Subject dev = user("developer-client");
        assertEquals(Decision.ALLOW, decide(dev, Artifact.Write, "team-a/normal-schema"));
        assertEquals(Decision.DENY, decide(dev, Artifact.Read, "team-a/secret-schema"));
    }

    @Test
    void batchedWithDenyRules() {
        Subject dev = user("developer-client");
        List<Action> actions = List.of(
                new Action(Artifact.Read, "team-a/schema-1"),
                new Action(Artifact.Read, "team-a/secret-schema"),
                new Action(Artifact.Read, "shared/common")
        );
        AuthorizeResult result = authorizer.authorize(dev, actions).toCompletableFuture().join();
        assertEquals(2, result.allowed().size());
        assertEquals(1, result.denied().size());
        assertEquals("team-a/secret-schema", result.denied().get(0).resourceName());
    }

    // ==================== Exact artifact grants ====================

    @Test
    void exactArtifactGrantInDeniedGroup() {
        Subject dev = user("developer-client");
        assertEquals(Decision.ALLOW, decide(dev, Artifact.Read, "team-b/public-schema"));
        assertEquals(Decision.DENY, decide(dev, Artifact.Read, "team-b/private-schema"));
    }

    // ==================== SearchFilterData ====================

    @Test
    void searchFilterDataIncludesExactResources() {
        GrantsData data = authorizer.getGrantsData();
        SearchFilterData filterData = data.getSearchFilterData(
                "developer-client", Set.of(), "artifact", "/");
        assertNotNull(filterData);
        assertFalse(filterData.allowAll());
        assertTrue(filterData.allowedGroups().contains("team-a"));
        assertTrue(filterData.allowedGroups().contains("shared"));
        assertTrue(filterData.allowedExactResources().contains("team-b/public-schema"));
    }

    @Test
    void searchFilterDataIncludesDeniedResources() {
        GrantsData data = authorizer.getGrantsData();
        SearchFilterData filterData = data.getSearchFilterData(
                "developer-client", Set.of(), "artifact", "/");
        assertNotNull(filterData);
        assertTrue(filterData.hasDenyFilters());
        assertTrue(filterData.deniedExactResources().contains("team-a/secret-schema"));
    }

    @Test
    void searchFilterDataNoDenyForDifferentUser() {
        GrantsData data = authorizer.getGrantsData();
        SearchFilterData filterData = data.getSearchFilterData(
                "unknown-user", Set.of("sr-readonly"), "artifact", "/");
        assertNotNull(filterData);
        assertFalse(filterData.hasDenyFilters());
        assertTrue(filterData.deniedExactResources().isEmpty());
    }

    @Test
    void searchFilterDataDenyWithWildcardAllow() {
        String json = """
                {
                  "grants": [
                    {"principal": "wildcard-user", "operation": "read", "resource_type": "artifact",
                     "resource_pattern_type": "prefix", "resource_pattern": "*"},
                    {"principal": "wildcard-user", "operation": "read", "resource_type": "artifact",
                     "resource_pattern_type": "exact", "resource_pattern": "team-a/secret",
                     "deny": true}
                  ]
                }""";
        GrantsData data = GrantsData.parse(json);
        SearchFilterData filterData = data.getSearchFilterData(
                "wildcard-user", Set.of(), "artifact", "/");
        assertNotNull(filterData);
        assertTrue(filterData.allowAll());
        assertTrue(filterData.hasDenyFilters());
        assertTrue(filterData.deniedExactResources().contains("team-a/secret"));
    }

    @Test
    void searchFilterDataAdminCheckedSeparately() {
        GrantsData data = authorizer.getGrantsData();
        assertTrue(data.isAdmin(Set.of("sr-admin")));
    }

    // ==================== Permissions query ====================

    @Test
    void batchedPermissionsForUi() {
        Subject dev = user("developer-client");
        String resource = "team-a/schema-1";
        AuthorizeResult result = authorizer.authorize(dev, List.of(
                new Action(Artifact.Read, resource),
                new Action(Artifact.Write, resource),
                new Action(Artifact.Admin, resource)
        )).toCompletableFuture().join();

        assertEquals(Decision.ALLOW, result.decision(Artifact.Read, resource));
        assertEquals(Decision.ALLOW, result.decision(Artifact.Write, resource));
        assertEquals(Decision.DENY, result.decision(Artifact.Admin, resource));
    }
}
