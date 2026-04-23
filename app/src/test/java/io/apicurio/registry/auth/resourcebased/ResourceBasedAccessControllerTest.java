package io.apicurio.registry.auth.resourcebased;

import java.net.URL;
import java.util.List;
import java.util.concurrent.CompletionStage;

import io.kroxylicious.authorizer.provider.acl.AclAuthorizerConfig;
import io.kroxylicious.authorizer.provider.acl.AclAuthorizerService;
import io.kroxylicious.authorizer.service.Action;
import io.kroxylicious.authorizer.service.AuthorizeResult;
import io.kroxylicious.authorizer.service.Authorizer;
import io.kroxylicious.authorizer.service.Decision;
import io.kroxylicious.proxy.authentication.Subject;
import io.kroxylicious.proxy.authentication.User;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ResourceBasedAccessControllerTest {

    private Authorizer authorizer;

    @BeforeEach
    void setUp() {
        URL rulesUrl = getClass().getClassLoader().getResource("test-acl-rules.acl");
        Assertions.assertNotNull(rulesUrl, "test-acl-rules.acl not found on classpath");

        AclAuthorizerService service = new AclAuthorizerService();
        service.initialize(new AclAuthorizerConfig(rulesUrl.getPath()));
        authorizer = service.build();
    }

    @Test
    void aliceCanReadOwnArtifact() {
        Assertions.assertEquals(Decision.ALLOW, decide("alice", RegistryArtifact.Read, "team-a/my-schema"));
    }

    @Test
    void aliceCanWriteOwnArtifact() {
        Assertions.assertEquals(Decision.ALLOW, decide("alice", RegistryArtifact.Write, "team-a/my-schema"));
    }

    @Test
    void aliceCannotWriteSharedArtifact() {
        Assertions.assertEquals(Decision.DENY, decide("alice", RegistryArtifact.Write, "shared/common-schema"));
    }

    @Test
    void aliceCanReadSharedArtifact() {
        Assertions.assertEquals(Decision.ALLOW, decide("alice", RegistryArtifact.Read, "shared/common-schema"));
    }

    @Test
    void aliceCannotReadTeamBArtifact() {
        Assertions.assertEquals(Decision.DENY, decide("alice", RegistryArtifact.Read, "team-b/their-schema"));
    }

    @Test
    void bobCanReadAnyArtifact() {
        Assertions.assertEquals(Decision.ALLOW, decide("bob", RegistryArtifact.Read, "team-a/my-schema"));
        Assertions.assertEquals(Decision.ALLOW, decide("bob", RegistryArtifact.Read, "team-b/their-schema"));
        Assertions.assertEquals(Decision.ALLOW, decide("bob", RegistryArtifact.Read, "shared/common-schema"));
    }

    @Test
    void bobCanWriteOnlyToTeamB() {
        Assertions.assertEquals(Decision.ALLOW, decide("bob", RegistryArtifact.Write, "team-b/their-schema"));
        Assertions.assertEquals(Decision.DENY, decide("bob", RegistryArtifact.Write, "team-a/my-schema"));
    }

    @Test
    void adminCanDoEverything() {
        Assertions.assertEquals(Decision.ALLOW, decide("admin", RegistryArtifact.Read, "team-a/x"));
        Assertions.assertEquals(Decision.ALLOW, decide("admin", RegistryArtifact.Write, "team-b/y"));
        Assertions.assertEquals(Decision.ALLOW, decide("admin", RegistryArtifact.Admin, "shared/z"));
    }

    @Test
    void unknownUserDenied() {
        Assertions.assertEquals(Decision.DENY, decide("unknown", RegistryArtifact.Read, "team-a/my-schema"));
    }

    @Test
    void anonymousUserDenied() {
        Subject anonymous = Subject.anonymous();
        AuthorizeResult result = authorizer.authorize(
                anonymous, List.of(new Action(RegistryArtifact.Read, "team-a/x"))
        ).toCompletableFuture().join();
        Assertions.assertEquals(Decision.DENY, result.decision(RegistryArtifact.Read, "team-a/x"));
    }

    @Test
    void batchedAuthorizationWorks() {
        Subject alice = new Subject(new User("alice"));
        List<Action> actions = List.of(
                new Action(RegistryArtifact.Read, "team-a/schema-1"),
                new Action(RegistryArtifact.Read, "team-a/schema-2"),
                new Action(RegistryArtifact.Read, "team-b/schema-3"),
                new Action(RegistryArtifact.Read, "shared/schema-4")
        );

        AuthorizeResult result = authorizer.authorize(alice, actions).toCompletableFuture().join();

        Assertions.assertEquals(3, result.allowed().size());
        Assertions.assertEquals(1, result.denied().size());
        Assertions.assertEquals("team-b/schema-3", result.denied().get(0).resourceName());
    }

    @Test
    void searchResultFilteringWithPartition() {
        Subject alice = new Subject(new User("alice"));
        List<String> searchResults = List.of(
                "team-a/schema-1", "team-a/schema-2",
                "team-b/schema-3", "shared/schema-4",
                "team-b/schema-5", "shared/schema-6"
        );

        List<Action> actions = searchResults.stream()
                .map(name -> new Action(RegistryArtifact.Read, name))
                .toList();

        AuthorizeResult result = authorizer.authorize(alice, actions).toCompletableFuture().join();

        List<String> allowed = result.allowed(RegistryArtifact.Read);
        List<String> denied = result.denied(RegistryArtifact.Read);

        Assertions.assertEquals(4, allowed.size());
        Assertions.assertTrue(allowed.contains("team-a/schema-1"));
        Assertions.assertTrue(allowed.contains("team-a/schema-2"));
        Assertions.assertTrue(allowed.contains("shared/schema-4"));
        Assertions.assertTrue(allowed.contains("shared/schema-6"));

        Assertions.assertEquals(2, denied.size());
        Assertions.assertTrue(denied.contains("team-b/schema-3"));
        Assertions.assertTrue(denied.contains("team-b/schema-5"));
    }

    @Test
    void groupLevelAuthorizationWorks() {
        Assertions.assertEquals(Decision.ALLOW, decide("alice", RegistryGroup.Read, "team-a"));
        Assertions.assertEquals(Decision.ALLOW, decide("alice", RegistryGroup.Write, "team-a"));
        Assertions.assertEquals(Decision.DENY, decide("alice", RegistryGroup.Admin, "team-a"));
        Assertions.assertEquals(Decision.DENY, decide("alice", RegistryGroup.Read, "team-b"));
        Assertions.assertEquals(Decision.ALLOW, decide("admin", RegistryGroup.Admin, "team-b"));
    }

    @Test
    void impliesWorks() {
        URL rulesUrl = getClass().getClassLoader().getResource("test-acl-implies.acl");
        Assertions.assertNotNull(rulesUrl);

        AclAuthorizerService service = new AclAuthorizerService();
        service.initialize(new AclAuthorizerConfig(rulesUrl.getPath()));
        Authorizer authz = service.build();

        Assertions.assertEquals(Decision.ALLOW, decideWith(authz, "superuser", RegistryArtifact.Admin, "x"));
        Assertions.assertEquals(Decision.ALLOW, decideWith(authz, "superuser", RegistryArtifact.Write, "x"));
        Assertions.assertEquals(Decision.ALLOW, decideWith(authz, "superuser", RegistryArtifact.Read, "x"));
    }

    private Decision decide(String username, io.kroxylicious.authorizer.service.ResourceType<?> operation, String resourceName) {
        return decideWith(authorizer, username, operation, resourceName);
    }

    private Decision decideWith(Authorizer authz, String username, io.kroxylicious.authorizer.service.ResourceType<?> operation, String resourceName) {
        Subject subject = new Subject(new User(username));
        CompletionStage<AuthorizeResult> resultStage = authz.authorize(
                subject, List.of(new Action(operation, resourceName)));
        AuthorizeResult result = resultStage.toCompletableFuture().join();
        return result.decision(operation, resourceName);
    }
}
