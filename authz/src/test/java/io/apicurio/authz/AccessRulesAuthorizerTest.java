package io.apicurio.authz;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.kroxylicious.authorizer.service.Action;
import io.kroxylicious.authorizer.service.Decision;
import io.kroxylicious.authorizer.service.ResourceType;
import io.kroxylicious.identity.Principal;
import io.kroxylicious.identity.Subject;
import io.kroxylicious.proxy.authentication.User;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessRulesAuthorizerTest {

    private static final Set<Class<? extends ResourceType<?>>> REGISTRY_TYPES = Set.of(Artifact.class, Group.class);

    private static final String HEADER = "from io.apicurio.authz import Artifact, Group, Role;\n";

    private static final String RULES = HEADER + """
            deny User with name = "bob" to * Artifact with name = "team-a/secret";
            allow Role with name = "team-a-dev" to WRITE Artifact with name like "team-a/*";
            allow Role with name = "team-a-dev" to READ Group with name = "team-a";
            allow User with name = "carol" to READ Artifact with name in {"x/1", "x/2"};
            allow Role with name = "$anonymous" to READ Artifact with name like "public/*";
            otherwise deny;
            """;

    @TempDir
    Path dir;

    private Path write(String content, long modifiedOffsetMs) throws IOException {
        Path file = dir.resolve("rules.acl");
        Files.writeString(file, content);
        Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis() + modifiedOffsetMs));
        return file;
    }

    private static Subject subject(Principal... principals) {
        return new Subject(Set.of(principals));
    }

    private static Decision decide(AccessRulesAuthorizer authorizer, Subject subject, ResourceType<?> op,
            String name) {
        return authorizer.authorize(subject, List.of(new Action(op, name))).toCompletableFuture().join()
                .decision(op, name);
    }

    @Test
    void pointAccessFollowsTheRules() throws Exception {
        AccessRulesAuthorizer rules = AccessRulesAuthorizer.create(write(RULES, 0), REGISTRY_TYPES);
        Subject bob = subject(new User("bob"), new Role("team-a-dev"));

        assertEquals(Decision.ALLOW, decide(rules, bob, Artifact.WRITE, "team-a/schema"));
        // WRITE implies READ
        assertEquals(Decision.ALLOW, decide(rules, bob, Artifact.READ, "team-a/schema"));
        assertEquals(Decision.DENY, decide(rules, bob, Artifact.ADMIN, "team-a/schema"));
        assertEquals(Decision.DENY, decide(rules, bob, Artifact.READ, "team-b/schema"));
        assertEquals(Decision.ALLOW, decide(rules, bob, Group.READ, "team-a"));
        assertEquals(Decision.DENY, decide(rules, bob, Group.WRITE, "team-a"));
        // The deny wins over the prefix allow
        assertEquals(Decision.DENY, decide(rules, bob, Artifact.READ, "team-a/secret"));

        Subject carol = subject(new User("carol"));
        assertEquals(Decision.ALLOW, decide(rules, carol, Artifact.READ, "x/2"));
        assertEquals(Decision.DENY, decide(rules, carol, Artifact.READ, "x/3"));
        assertEquals(Decision.DENY, decide(rules, carol, Artifact.WRITE, "x/2"));
    }

    @Test
    void anonymousCallersMatchOnlyTheAnonymousRole() throws Exception {
        AccessRulesAuthorizer rules = AccessRulesAuthorizer.create(write(RULES, 0), REGISTRY_TYPES);

        assertEquals(Decision.ALLOW, decide(rules, subject(new Role("$anonymous")), Artifact.READ, "public/a"));
        // A subject with no principals matches no rule
        assertEquals(Decision.DENY, decide(rules, Subject.anonymous(), Artifact.READ, "public/a"));
        // A user named like the role is not the role
        assertEquals(Decision.DENY, decide(rules, subject(new User("$anonymous")), Artifact.READ, "public/a"));
    }

    /**
     * Pins the deny semantics of the Kroxylicious rules, which differ from Registry's former JSON
     * grants: a deny expands to the operations its operation implies, and nothing else.
     */
    @Test
    void denyCoversImpliedOperationsOnly() throws Exception {
        AccessRulesAuthorizer rules = AccessRulesAuthorizer.create(write(HEADER + """
                deny User with name = "bob" to WRITE Artifact with name = "a/locked";
                deny User with name = "bob" to READ Artifact with name = "a/hidden";
                allow User with name = "bob" to WRITE Artifact with name like "a/*";
                otherwise deny;
                """, 0), REGISTRY_TYPES);
        Subject bob = subject(new User("bob"));

        // Denying WRITE also denies READ (WRITE implies READ): it does not make the artifact read-only
        assertEquals(Decision.DENY, decide(rules, bob, Artifact.WRITE, "a/locked"));
        assertEquals(Decision.DENY, decide(rules, bob, Artifact.READ, "a/locked"));
        // Denying READ does not deny WRITE, which implies READ but is not implied by it
        assertEquals(Decision.DENY, decide(rules, bob, Artifact.READ, "a/hidden"));
        assertEquals(Decision.ALLOW, decide(rules, bob, Artifact.WRITE, "a/hidden"));
    }

    /** A WRITE deny leaves ADMIN to an ADMIN allow: ADMIN implies WRITE, not the reverse. */
    @Test
    void writeDenyDoesNotDenyAdmin() throws Exception {
        AccessRulesAuthorizer rules = AccessRulesAuthorizer.create(write(HEADER + """
                deny User with name = "bob" to WRITE Artifact with name = "a/x";
                deny User with name = "bob" to * Artifact with name = "a/y";
                allow User with name = "bob" to * Artifact with name *;
                otherwise deny;
                """, 0), REGISTRY_TYPES);
        Subject bob = subject(new User("bob"));

        assertEquals(Decision.DENY, decide(rules, bob, Artifact.READ, "a/x"));
        assertEquals(Decision.DENY, decide(rules, bob, Artifact.WRITE, "a/x"));
        assertEquals(Decision.ALLOW, decide(rules, bob, Artifact.ADMIN, "a/x"));
        // '*' (or ADMIN) denies all three
        assertEquals(Decision.DENY, decide(rules, bob, Artifact.ADMIN, "a/y"));
    }

    @Test
    void invalidFilesAreRejected() throws IOException {
        for (String invalid : List.of(
                HEADER + "allow Role with name = \"r\" to READ Artifact with name = \"x\";\n",
                HEADER + "allow Role with name = \"r\" to READ Artifact with name = \"x\";\n"
                        + "deny Role with name = \"r\" to READ Artifact with name = \"y\";\notherwise deny;\n",
                HEADER + "allow Role with name = \"r\" to DELETE Artifact with name = \"x\";\notherwise deny;\n",
                HEADER + "allow Role with name = \"r\" to READ Artifact with name matching /(/;\notherwise deny;\n",
                "allow Role with name = \"r\" to READ Artifact with name = \"x\";\notherwise deny;\n")) {
            Path file = write(invalid, 0);
            assertThrows(IllegalArgumentException.class, () -> AccessRulesAuthorizer.create(file, REGISTRY_TYPES),
                    invalid);
        }
        assertThrows(IOException.class,
                () -> AccessRulesAuthorizer.create(dir.resolve("missing.acl"), REGISTRY_TYPES));
    }

    @Test
    void rulesForTypesThatAreNotEnforcedAreRejected() throws IOException {
        Path file = write("from io.apicurio.authz import Artifact, Topic;\n"
                + "allow User with name = \"bob\" to READ Topic with name = \"orders\";\n"
                + "allow User with name = \"bob\" to READ Artifact with name = \"a/x\";\notherwise deny;\n", 0);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> AccessRulesAuthorizer.create(file, REGISTRY_TYPES));
        assertTrue(e.getMessage().contains("io.apicurio.authz.Topic"), e.getMessage());

        AccessRulesAuthorizer rules = AccessRulesAuthorizer.create(write(RULES, 0), REGISTRY_TYPES);
        assertEquals(Optional.of(REGISTRY_TYPES), rules.supportedResourceTypes());
    }

    @Test
    void reloadAppliesValidChangesAndKeepsTheRulesOnInvalidOnes() throws Exception {
        Path file = write(RULES, 0);
        AccessRulesAuthorizer rules = AccessRulesAuthorizer.create(file, REGISTRY_TYPES);
        Subject dave = subject(new User("dave"));
        assertFalse(rules.checkForChanges());
        assertEquals(Decision.DENY, decide(rules, dave, Artifact.READ, "d/x"));

        write(HEADER + "allow User with name = \"dave\" to READ Artifact with name like \"d/*\";\notherwise deny;\n",
                10_000);
        assertTrue(rules.checkForChanges());
        assertEquals(Files.getLastModifiedTime(file), rules.getLastSeenModified());
        assertEquals(Decision.ALLOW, decide(rules, dave, Artifact.READ, "d/x"));

        FileTime invalidVersion = Files.getLastModifiedTime(write("allow nonsense;", 20_000));
        assertFalse(rules.checkForChanges());
        assertEquals(invalidVersion, rules.getLastSeenModified());
        assertEquals(Decision.ALLOW, decide(rules, dave, Artifact.READ, "d/x"));
        // The rejected version is not processed again on the next poll
        assertFalse(rules.checkForChanges());
        assertEquals(invalidVersion, rules.getLastSeenModified());
    }
}
