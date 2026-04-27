package io.apicurio.authz;

import java.net.URL;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.kroxylicious.authorizer.service.Action;
import io.kroxylicious.authorizer.service.AuthorizeResult;
import io.kroxylicious.authorizer.service.Decision;
import io.kroxylicious.authorizer.service.ResourceType;
import io.kroxylicious.proxy.authentication.Subject;
import io.kroxylicious.proxy.authentication.User;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpaWasmAuthorizerTest {

    enum RegistryArtifact implements ResourceType<RegistryArtifact> {
        Read, Write, Admin;
        @Override
        public Set<RegistryArtifact> implies() {
            return switch (this) {
                case Admin -> Set.of(Write, Read);
                case Write -> Set.of(Read);
                case Read -> Set.of();
            };
        }
    }

    enum RegistryGroup implements ResourceType<RegistryGroup> {
        Read, Write, Admin
    }

    enum Topic implements ResourceType<Topic> {
        Read, Write, Create, Delete
    }

    enum Dashboard implements ResourceType<Dashboard> {
        Read, Write
    }

    private static OpaWasmAuthorizer authorizer;

    @BeforeAll
    static void setUp() throws Exception {
        URL wasmUrl = OpaWasmAuthorizerTest.class.getClassLoader().getResource("default-authz.wasm");
        URL grantsUrl = OpaWasmAuthorizerTest.class.getClassLoader().getResource("test-grants.json");
        assertNotNull(wasmUrl);
        assertNotNull(grantsUrl);
        authorizer = OpaWasmAuthorizer.create(Path.of(wasmUrl.toURI()), Path.of(grantsUrl.toURI()), 2,
                Map.of(
                        RegistryArtifact.class, "artifact",
                        RegistryGroup.class, "group",
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
        var principals = new java.util.HashSet<io.kroxylicious.proxy.authentication.Principal>();
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
        assertEquals(Decision.ALLOW, decide(user("developer-client"), RegistryArtifact.Read, "team-a/schema-1"));
    }

    @Test
    void developerCanWriteOwnArtifact() {
        assertEquals(Decision.ALLOW, decide(user("developer-client"), RegistryArtifact.Write, "team-a/schema-1"));
    }

    @Test
    void developerCanReadShared() {
        assertEquals(Decision.ALLOW, decide(user("developer-client"), RegistryArtifact.Read, "shared/common"));
    }

    @Test
    void developerCannotWriteShared() {
        assertEquals(Decision.DENY, decide(user("developer-client"), RegistryArtifact.Write, "shared/common"));
    }

    @Test
    void developerCannotReadTeamB() {
        assertEquals(Decision.DENY, decide(user("developer-client"), RegistryArtifact.Read, "team-b/secret"));
    }

    // ==================== Admin bypass ====================

    @Test
    void adminCanDoAnything() {
        Subject admin = user("superuser", "sr-admin");
        assertEquals(Decision.ALLOW, decide(admin, RegistryArtifact.Admin, "any-group/any-artifact"));
        assertEquals(Decision.ALLOW, decide(admin, Topic.Write, "any-topic"));
        assertEquals(Decision.ALLOW, decide(admin, Dashboard.Write, "any-dashboard"));
    }

    // ==================== Role-based grants ====================

    @Test
    void readonlyRoleCanReadShared() {
        Subject readonly = user("readonly-client", "sr-readonly");
        assertEquals(Decision.ALLOW, decide(readonly, RegistryArtifact.Read, "shared/common"));
    }

    @Test
    void readonlyRoleCannotReadTeamA() {
        Subject readonly = user("readonly-client", "sr-readonly");
        assertEquals(Decision.DENY, decide(readonly, RegistryArtifact.Read, "team-a/schema"));
    }

    @Test
    void developerRoleCanReadPublic() {
        Subject dev = user("anyone", "sr-developer");
        assertEquals(Decision.ALLOW, decide(dev, RegistryArtifact.Read, "public/common-schema"));
    }

    // ==================== Batched authorization ====================

    @Test
    void batchedAuthorizationWorks() {
        Subject dev = user("developer-client");
        List<Action> actions = List.of(
                new Action(RegistryArtifact.Read, "team-a/schema-1"),
                new Action(RegistryArtifact.Read, "team-a/schema-2"),
                new Action(RegistryArtifact.Read, "team-b/secret"),
                new Action(RegistryArtifact.Read, "shared/common")
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
                .map(name -> new Action(RegistryArtifact.Read, name))
                .toList();

        AuthorizeResult result = authorizer.authorize(dev, actions).toCompletableFuture().join();
        Map<Decision, List<String>> partitioned = result.partition(
                searchResults, RegistryArtifact.Read, name -> name);

        assertEquals(3, partitioned.get(Decision.ALLOW).size());
        assertEquals(1, partitioned.get(Decision.DENY).size());
        assertTrue(partitioned.get(Decision.ALLOW).contains("team-a/schema-1"));
        assertTrue(partitioned.get(Decision.DENY).contains("team-b/secret"));
    }

    // ==================== Cross-system resource types ====================

    @Test
    void sameFileMultipleResourceTypes() {
        Subject dev = user("developer-client");
        assertEquals(Decision.ALLOW, decide(dev, RegistryArtifact.Write, "team-a/schema"));
        assertEquals(Decision.DENY, decide(dev, Topic.Write, "team-a.events"));
    }

    // ==================== Unknown user ====================

    @Test
    void unknownUserDenied() {
        assertEquals(Decision.DENY, decide(user("unknown"), RegistryArtifact.Read, "team-a/x"));
    }

    @Test
    void anonymousDenied() {
        AuthorizeResult result = authorizer.authorize(Subject.anonymous(),
                List.of(new Action(RegistryArtifact.Read, "team-a/x")))
                .toCompletableFuture().join();
        assertEquals(Decision.DENY, result.decision(RegistryArtifact.Read, "team-a/x"));
    }

    // ==================== GrantsData ====================

    @Test
    void grantsDataAllowedValues() {
        GrantsData data = authorizer.getGrantsData();
        Set<String> groups = data.getAllowedValues("developer-client", Set.of(), "artifact", "/");
        assertNotNull(groups);
        assertTrue(groups.contains("team-a"));
        assertTrue(groups.contains("shared"));
    }

    @Test
    void grantsDataMalformedJson() {
        GrantsData data = GrantsData.parse("broken{{{");
        assertFalse(data.isAdmin(Set.of("sr-admin")));
        assertTrue(data.getGrantsForUser("anyone", Set.of()).isEmpty());
    }

    @Test
    void perUserJsonCached() {
        GrantsData data = authorizer.getGrantsData();
        String json1 = data.getDataJsonForUser("developer-client", Set.of());
        String json2 = data.getDataJsonForUser("developer-client", Set.of());
        assertTrue(json1 == json2);
    }
}
