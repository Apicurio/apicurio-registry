package io.apicurio.authz;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.kroxylicious.authorizer.service.Action;
import io.kroxylicious.authorizer.service.AuthorizeResult;
import io.kroxylicious.authorizer.service.Decision;
import io.kroxylicious.authorizer.service.ResourceType;
import io.kroxylicious.identity.Principal;
import io.kroxylicious.identity.Subject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
            return new Subject(Set.of(new User(name)));
        }
        var principals = new HashSet<Principal>();
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

    // ==================== Prefix deny rules ====================

    @Test
    void prefixDenyRuleBlocksMatchingArtifacts() {
        Subject dev = user("developer-client");
        assertEquals(Decision.DENY, decide(dev, Artifact.Read, "team-a/internal-api"));
        assertEquals(Decision.DENY, decide(dev, Artifact.Read, "team-a/internal-config"));
        assertEquals(Decision.ALLOW, decide(dev, Artifact.Read, "team-a/schema-1"));
    }

    @Test
    void prefixDenyDoesNotAffectOtherGroups() {
        Subject dev = user("developer-client");
        assertEquals(Decision.ALLOW, decide(dev, Artifact.Read, "shared/common"));
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
    void searchFilterDataKeepsPatternsVerbatim() {
        SearchFilterData data = authorizer.getGrantsData()
                .getSearchFilterData("developer-client", Set.of(), "artifact");

        assertFalse(data.allowAll());
        assertEquals(Set.of("team-b/public-schema"), data.allowedExact());
        assertEquals(Set.of("team-a/", "shared/"), data.allowedPrefix());
        assertEquals(Set.of("team-a/secret-schema"), data.deniedExact());
        assertEquals(Set.of("team-a/internal-"), data.deniedPrefix());
    }

    @Test
    void searchFilterDataForRoleWithoutDenies() {
        SearchFilterData data = authorizer.getGrantsData()
                .getSearchFilterData("unknown-user", Set.of("sr-readonly"), "artifact");

        assertEquals(Set.of("shared/"), data.allowedPrefix());
        assertTrue(data.deniedExact().isEmpty());
        assertTrue(data.deniedPrefix().isEmpty());
    }

    @Test
    void searchFilterDataUnknownUserAllowsNothing() {
        SearchFilterData data = authorizer.getGrantsData()
                .getSearchFilterData("nobody", Set.of(), "artifact");

        assertTrue(data.allowsNothing());
    }

    @Test
    void searchFilterDataWildcardAllowWithDeny() {
        GrantsData data = GrantsData.parse("""
                {"grants": [
                  {"principal": "u", "operation": "read", "resource_type": "artifact",
                   "resource_pattern_type": "prefix", "resource_pattern": "*"},
                  {"principal": "u", "operation": "read", "resource_type": "artifact",
                   "resource_pattern_type": "exact", "resource_pattern": "team-a/secret", "deny": true}
                ]}""");

        SearchFilterData filter = data.getSearchFilterData("u", Set.of(), "artifact");

        assertTrue(filter.allowAll());
        assertFalse(filter.allowsEverything());
        assertEquals(Set.of("team-a/secret"), filter.deniedExact());
    }

    @Test
    void searchFilterDataWildcardDenyAllowsNothing() {
        GrantsData data = GrantsData.parse("""
                {"grants": [
                  {"principal": "u", "operation": "read", "resource_type": "artifact",
                   "resource_pattern_type": "prefix", "resource_pattern": "team-a/"},
                  {"principal": "u", "operation": "read", "resource_type": "artifact",
                   "resource_pattern": "*", "deny": true}
                ]}""");

        assertTrue(data.getSearchFilterData("u", Set.of(), "artifact").allowsNothing());
    }

    @Test
    void searchFilterDataIgnoresGrantsThatDoNotImplyRead() {
        GrantsData data = GrantsData.parse("""
                {"grants": [
                  {"principal": "u", "operation": "something-else", "resource_type": "artifact",
                   "resource_pattern_type": "prefix", "resource_pattern": "team-a/"}
                ]}""");

        assertTrue(data.getSearchFilterData("u", Set.of(), "artifact").allowsNothing());
    }

    /**
     * The core search invariant: for every subject, the search patterns select exactly the
     * resources point access allows, including names whose group contains '/', the default group
     * and near-miss prefixes.
     */
    @Test
    void searchFilterDataMatchesPointAccessForEveryResource() throws Exception {
        String json = """
                {"config": {"admin_roles": ["sr-admin"]},
                 "grants": [
                  {"principal": "alice", "operation": "write", "resource_type": "artifact",
                   "resource_pattern_type": "prefix", "resource_pattern": "team-a/"},
                  {"principal": "alice", "operation": "read", "resource_type": "artifact",
                   "resource_pattern_type": "prefix", "resource_pattern": "team-a/secret/", "deny": true},
                  {"principal": "alice", "operation": "read", "resource_type": "artifact",
                   "resource_pattern_type": "exact", "resource_pattern": "default/shared-schema"},
                  {"principal": "bob", "operation": "read", "resource_type": "artifact",
                   "resource_pattern_type": "prefix", "resource_pattern": "team-"},
                  {"principal": "bob", "operation": "read", "resource_type": "artifact",
                   "resource_pattern_type": "exact", "resource_pattern": "team-b/private", "deny": true},
                  {"principal": "carol", "operation": "read", "resource_type": "artifact",
                   "resource_pattern_type": "exact", "resource_pattern": "team-a"},
                  {"principal": "dave", "operation": "read", "resource_type": "artifact",
                   "resource_pattern": "*"},
                  {"principal": "dave", "operation": "read", "resource_type": "artifact",
                   "resource_pattern_type": "prefix", "resource_pattern": "def", "deny": true},
                  {"principal_role": "readers", "operation": "read", "resource_type": "artifact",
                   "resource_pattern_type": "prefix", "resource_pattern": "public/"}
                ]}""";
        List<String> names = List.of("team-a/x", "team-a/secret/y", "team-a/secret", "team-a/secretive",
                "team-a", "team-ab/x", "team-b/private", "team-b/private2", "team-/x",
                "default/shared-schema", "default/other", "defaults/x", "public/p", "public",
                "team-a/secret/nested/z", "other/team-a/x");
        Path file = Files.createTempFile("invariant-grants", ".json");
        try {
            Files.writeString(file, json);
            try (GrantsAuthorizer authz = GrantsAuthorizer.create(file, Map.of(Artifact.class, "artifact"))) {
                List<Subject> subjects = List.of(user("alice"), user("bob"), user("carol"), user("dave"),
                        user("erin", "readers"), user("nobody"), Subject.anonymous());
                for (Subject subject : subjects) {
                    String name = subject.uniquePrincipalOfType(User.class).map(User::name).orElse(null);
                    Set<String> roles = new HashSet<>();
                    subject.allPrincipalsOfType(RolePrincipal.class).forEach(r -> roles.add(r.name()));
                    SearchFilterData filter = authz.getGrantsData().getSearchFilterData(name, roles, "artifact");
                    for (String resource : names) {
                        Decision pointAccess = authz.authorize(subject, List.of(new Action(Artifact.Read, resource)))
                                .toCompletableFuture().join().decision(Artifact.Read, resource);
                        assertEquals(pointAccess == Decision.ALLOW, filter.matches(resource),
                                "search/point-access mismatch for " + name + " on " + resource);
                    }
                }
            }
        } finally {
            Files.deleteIfExists(file);
        }
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

    // ==================== Anonymous principal isolation ====================

    @Test
    void namedAnonymousPrincipalGrantDoesNotMatchAnonymousSubject() throws Exception {
        String json = """
                {
                  "grants": [
                    {"principal": "anonymous", "operation": "read", "resource_type": "artifact",
                     "resource_pattern_type": "prefix", "resource_pattern": "team-a/"}
                  ]
                }""";
        Path tempFile = Files.createTempFile("anon-grants", ".json");
        try {
            Files.writeString(tempFile, json);
            GrantsAuthorizer anonAuthorizer = GrantsAuthorizer.create(tempFile,
                    Map.of(Artifact.class, "artifact"));
            try {
                GrantsData data = anonAuthorizer.getGrantsData();
                // A real user literally named "anonymous" should still get their grant...
                assertEquals(1, data.getGrantsForUser("anonymous", Set.of()).size());
                // ...but an actually-anonymous (unauthenticated) subject must never match it.
                AuthorizeResult result = anonAuthorizer.authorize(Subject.anonymous(),
                        List.of(new Action(Artifact.Read, "team-a/x"))).toCompletableFuture().join();
                assertEquals(Decision.DENY, result.decision(Artifact.Read, "team-a/x"));
            } finally {
                anonAuthorizer.close();
            }
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    // ==================== Kroxylicious Authorizer contract ====================

    @Test
    void supportedResourceTypesReflectsConfiguredMapping() {
        Optional<Set<Class<? extends ResourceType<?>>>> supported = authorizer.supportedResourceTypes();
        assertTrue(supported.isPresent());
        assertEquals(Set.of(Artifact.class, Group.class, Topic.class, Dashboard.class), supported.get());
    }

    @Test
    void supportedResourceTypesEmptyWhenNoMappingConfigured() throws Exception {
        try (GrantsAuthorizer unmapped = GrantsAuthorizer.create(null)) {
            assertEquals(Optional.empty(), unmapped.supportedResourceTypes());
        }
    }

    record ClientIdPrincipal(String name) implements Principal {
    }

    @Test
    void foreignPrincipalTypeIsNotTreatedAsRole() {
        // A principal contributed by another system whose name happens to equal a role
        // must not satisfy a principal_role grant; only RolePrincipal counts as a role.
        Subject subject = new Subject(Set.of(new User("readonly-client"), new ClientIdPrincipal("sr-readonly")));
        assertEquals(Decision.DENY, decide(subject, Artifact.Read, "shared/common"));

        Subject withRole = user("readonly-client", "sr-readonly");
        assertEquals(Decision.ALLOW, decide(withRole, Artifact.Read, "shared/common"));
    }

    @Test
    void foreignPrincipalTypeCannotGrantAdmin() {
        Subject subject = new Subject(Set.of(new User("someone"), new ClientIdPrincipal("sr-admin")));
        assertEquals(Decision.DENY, decide(subject, Artifact.Read, "team-a/schema-1"));
    }

    // ==================== Loading and hot reload ====================

    private static final String ONE_GRANT = """
            {"grants": [{"principal": "u", "operation": "read", "resource_type": "artifact",
              "resource_pattern_type": "prefix", "resource_pattern": "team-a/"}]}""";

    @Test
    void createFailsWhenFileIsMissing() {
        Path missing = Path.of("does-not-exist-" + System.nanoTime() + ".json");

        assertThrows(IOException.class, () -> GrantsAuthorizer.create(missing, Map.of()));
    }

    @Test
    void createFailsOnMalformedFile() throws Exception {
        for (String bad : List.of("broken{{{", "[]", "{\"grant\": []}")) {
            Path file = Files.createTempFile("bad-grants", ".json");
            try {
                Files.writeString(file, bad);
                assertThrows(IllegalArgumentException.class, () -> GrantsAuthorizer.create(file, Map.of()), bad);
            } finally {
                Files.deleteIfExists(file);
            }
        }
    }

    @Test
    void reloadPicksUpChanges() throws Exception {
        Path file = Files.createTempFile("reload-grants", ".json");
        try {
            Files.writeString(file, ONE_GRANT);
            try (GrantsAuthorizer authz = GrantsAuthorizer.create(file, Map.of(Artifact.class, "artifact"))) {
                assertEquals(Decision.DENY, decideWith(authz, user("v"), "team-a/x"));

                Files.writeString(file, ONE_GRANT.replace("\"u\"", "\"v\""));
                Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis() + 10_000));

                assertTrue(authz.checkForDataFileChanges());
                assertEquals(Decision.ALLOW, decideWith(authz, user("v"), "team-a/x"));
                assertFalse(authz.checkForDataFileChanges());
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void invalidReloadKeepsPreviousGrants() throws Exception {
        Path file = Files.createTempFile("reload-grants", ".json");
        try {
            Files.writeString(file, ONE_GRANT);
            try (GrantsAuthorizer authz = GrantsAuthorizer.create(file, Map.of(Artifact.class, "artifact"))) {
                Files.writeString(file, "{ this is not json");
                Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis() + 10_000));

                assertFalse(authz.checkForDataFileChanges());
                assertEquals(Decision.ALLOW, decideWith(authz, user("u"), "team-a/x"));
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private static Decision decideWith(GrantsAuthorizer authz, Subject subject, String resource) {
        return authz.authorize(subject, List.of(new Action(Artifact.Read, resource)))
                .toCompletableFuture().join().decision(Artifact.Read, resource);
    }

    // ==================== Deny operation semantics ====================

    private Decision decideOp(GrantsAuthorizer authz, Artifact op, String user, String resource) {
        return authz.authorize(user(user), List.of(new Action(op, resource)))
                .toCompletableFuture().join().decision(op, resource);
    }

    @Test
    void denyWriteMakesResourceReadOnlyAndSearchStillShowsIt() throws Exception {
        Path file = Files.createTempFile("deny-ops", ".json");
        try {
            Files.writeString(file, """
                    {"grants": [
                      {"principal": "u", "operation": "admin", "resource_type": "artifact",
                       "resource_pattern_type": "prefix", "resource_pattern": "team-a/"},
                      {"principal": "u", "operation": "write", "resource_type": "artifact",
                       "resource_pattern_type": "exact", "resource_pattern": "team-a/frozen", "deny": true},
                      {"principal": "u", "operation": "read", "resource_type": "artifact",
                       "resource_pattern_type": "exact", "resource_pattern": "team-a/hidden", "deny": true}
                    ]}""");
            try (GrantsAuthorizer authz = GrantsAuthorizer.create(file, Map.of(Artifact.class, "artifact"))) {
                assertEquals(Decision.ALLOW, decideOp(authz, Artifact.Read, "u", "team-a/frozen"));
                assertEquals(Decision.DENY, decideOp(authz, Artifact.Write, "u", "team-a/frozen"));
                assertEquals(Decision.DENY, decideOp(authz, Artifact.Admin, "u", "team-a/frozen"));

                assertEquals(Decision.DENY, decideOp(authz, Artifact.Read, "u", "team-a/hidden"));
                assertEquals(Decision.DENY, decideOp(authz, Artifact.Write, "u", "team-a/hidden"));

                SearchFilterData search = authz.getGrantsData().getSearchFilterData("u", Set.of(), "artifact");
                assertTrue(search.matches("team-a/frozen"));
                assertFalse(search.matches("team-a/hidden"));
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
