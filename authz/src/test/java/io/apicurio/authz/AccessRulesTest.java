package io.apicurio.authz;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.apicurio.scope.ResourceScope;
import io.kroxylicious.authorizer.provider.acl.AclAuthorizerConfig;
import io.kroxylicious.authorizer.provider.acl.AclAuthorizerService;
import io.kroxylicious.authorizer.service.Action;
import io.kroxylicious.authorizer.service.Authorizer;
import io.kroxylicious.authorizer.service.Decision;
import io.kroxylicious.authorizer.service.ResourceType;
import io.kroxylicious.identity.Principal;
import io.kroxylicious.identity.Subject;
import io.kroxylicious.proxy.authentication.User;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AccessRules} must read rules files exactly as Kroxylicious does: its authorizer must decide
 * as the one {@code AclAuthorizerService} builds, it must reject the same files, and its search
 * scope must select exactly the names that point access allows. Checked for every subject,
 * operation and name of a fixture covering every selector.
 */
class AccessRulesTest {

    static final String RULES = """
            from io.apicurio.authz import Artifact, Group, Role, ClientId;
            deny User with name = "bob" to * Artifact with name = "team-a/secret";
            deny Role with name = "contractors" to READ Artifact with name like "team-a/internal-*";
            deny User with name like "temp-*" to WRITE Artifact with name matching /.*-locked/;
            deny Role with name in {"auditors", "guests"} to WRITE Group with name *;
            allow Role with name = "team-a-dev" to WRITE Artifact with name like "team-a/*";
            allow Role with name = "contractors" to READ Artifact with name like "team-a/*";
            allow User with name like "ali*" to READ Artifact with name in {"x/1", "x/\\"quoted\\"", "team-a%2Fsub/y"};
            allow User with name = "bob" to ADMIN Artifact with name matching /team-(b|c)\\/[a-z]+/;
            allow User with name = "bob" to READ Artifact with name matching /dot\\.only/;
            allow User with name * to READ Group with name like "public*";
            allow User with name like "temp-*" to * Artifact with name like "scratch/*";
            allow Role with name = "auditors" to READ Group with name *;
            allow Role with name = "auditors" to READ Artifact with name like "*";
            allow Role with name = "exact-like" to READ Artifact with name like "literal";
            allow Role with name = "star" to READ Artifact with name like "lit\\*star*";
            allow ClientId with name = "svc" to {READ, WRITE} Artifact with name like "svc/*";
            otherwise deny;
            """;

    static final List<Subject> SUBJECTS = List.of(
            subject(new User("bob"), new Role("team-a-dev")),
            subject(new User("carol"), new Role("contractors")),
            subject(new User("alice")),
            subject(new User("ali")),
            subject(new User("al")),
            subject(new User("temp-joe")),
            subject(new User("dan"), new Role("auditors"), new Role("guests")),
            subject(new User("eve"), new ClientId("team-a-dev")),
            subject(new ClientId("svc")),
            subject(new Role("svc")),
            subject(new Role("exact-like")),
            subject(new Role("star")),
            Subject.anonymous());

    static final List<ResourceType<?>> OPERATIONS = List.of(
            Artifact.READ, Artifact.WRITE, Artifact.ADMIN, Group.READ, Group.WRITE, Group.ADMIN);

    static final List<String> NAMES = List.of(
            "team-a/schema", "team-a/secret", "team-a/internal-x", "team-a/internal-", "team-a", "team-a/",
            "team-ab/x", "TEAM-A/schema", "team-b/abc", "team-b/ABC", "team-c/x", "team-d/x", "team-b/abc/d",
            "x/1", "x/2", "x/\"quoted\"", "team-a%2Fsub/y", "team-a/sub/y", "public", "public-group", "pub",
            "scratch/a", "scratch/a-locked", "scratch-locked", "a-locked", "literal", "literal2", "lit*star-x",
            "litXstar", "dot.only", "dotxonly", "svc/a", "svc", "", "%", "_");

    @TempDir
    Path dir;

    static Subject subject(Principal... principals) {
        return new Subject(Set.of(principals));
    }

    private AccessRulesAuthorizer load() throws IOException {
        Path file = dir.resolve("rules.acl");
        Files.writeString(file, RULES);
        return AccessRulesAuthorizer.create(file, Set.of(Artifact.class, Group.class));
    }

    @Test
    void scopeMatchesPointAccessForEverySubjectOperationAndName() throws Exception {
        assertScopeMatchesPointAccess(load(), SUBJECTS, NAMES, 50);
    }

    /** Overlapping resource prefixes, and principal prefixes for principals of several classes. */
    static final String PREFIX_RULES = """
            from io.apicurio.authz import Artifact, Group, Role;
            deny User with name = "bob" to READ Artifact with name like "team-b/*";
            deny User with name = "bob" to READ Artifact with name like "team-b/internal-*";
            allow User with name = "bob" to READ Artifact with name like "team-a/*";
            allow User with name = "bob" to READ Artifact with name like "team-a/internal-*";
            allow User with name = "bob" to READ Artifact with name like "team-b/*";
            allow User with name = "bob" to READ Artifact with name like "team-c/*";
            allow Role with name like "reader-*" to READ Group with name *;
            allow User with name like "rea*" to READ Artifact with name like "u/*";
            otherwise deny;
            """;

    static final List<Subject> PREFIX_SUBJECTS = List.of(
            subject(new User("bob")),
            subject(new User("reader-bob")),
            subject(new Role("reader-bob")),
            subject(new Role("rea")),
            subject(new User("reader-bob"), new Role("other")));

    static final List<String> PREFIX_NAMES = List.of(
            "team-a/public", "team-a/internal-x", "team-a/a", "team-a/zzz", "team-b/public", "team-b/internal-x",
            "team-b/zzz", "team-c/x", "u/1", "u", "g");

    @Test
    void overlappingPrefixesAndPrincipalPrefixesAreEvaluatedPerRule() throws Exception {
        AccessRulesAuthorizer rules = AccessRulesAuthorizer.create(Files.writeString(dir.resolve("p.acl"), PREFIX_RULES),
                Set.of(Artifact.class, Group.class));
        Subject bob = subject(new User("bob"));
        // Every overlapping allow applies
        assertTrue(allowed(rules, bob, Artifact.READ, "team-a/public"));
        assertTrue(allowed(rules, bob, Artifact.READ, "team-a/zzz"));
        // Every overlapping deny applies
        assertFalse(allowed(rules, bob, Artifact.READ, "team-b/public"));
        assertFalse(allowed(rules, bob, Artifact.READ, "team-b/zzz"));
        assertTrue(allowed(rules, bob, Artifact.READ, "team-c/x"));
        // A Role prefix rule does not match a User whose name has the prefix
        assertFalse(allowed(rules, subject(new User("reader-bob")), Group.READ, "g"));
        assertTrue(allowed(rules, subject(new Role("reader-bob")), Group.READ, "g"));
        // ... and a User prefix rule does not match a Role
        assertTrue(allowed(rules, subject(new User("reader-bob")), Artifact.READ, "u/1"));
        assertFalse(allowed(rules, subject(new Role("rea")), Artifact.READ, "u/1"));

        assertScopeMatchesPointAccess(rules, PREFIX_SUBJECTS, PREFIX_NAMES, 5);
    }

    private static void assertScopeMatchesPointAccess(Authorizer authorizer, List<Subject> subjects,
            List<String> names, int minimumEach) {
        AccessRulesAuthorizer rules = (AccessRulesAuthorizer) authorizer;
        List<String> mismatches = new ArrayList<>();
        int allowed = 0;
        int checked = 0;
        for (Subject subject : subjects) {
            for (ResourceType<?> op : OPERATIONS) {
                ResourceScope scope = rules.resolveScope(subject, op).toCompletableFuture().join();
                for (String name : names) {
                    boolean pointAccess = rules.authorize(subject, List.of(new Action(op, name)))
                            .toCompletableFuture().join().decision(op, name) == Decision.ALLOW;
                    allowed += pointAccess ? 1 : 0;
                    checked++;
                    if (pointAccess != scope.matches(name)) {
                        mismatches.add(subject.principals() + " " + op + " '" + name + "': point access "
                                + pointAccess + ", scope " + !pointAccess);
                    }
                }
            }
        }
        assertEquals(List.of(), mismatches);
        // Guard against a vacuous check: plenty of combinations are allowed, plenty denied
        assertTrue(allowed > minimumEach, "allowed: " + allowed);
        assertTrue(checked - allowed > minimumEach, "denied: " + (checked - allowed));
    }

    /** The authorizer {@code AclAuthorizerService} builds from a file, as Kroxylicious does. */
    private Authorizer kroxyliciousAuthorizer(String rules) throws IOException {
        Path file = dir.resolve("kroxylicious.acl");
        Files.writeString(file, rules);
        AclAuthorizerService service = new AclAuthorizerService();
        service.initialize(new AclAuthorizerConfig(file.toString()));
        return service.build();
    }

    private static ResourceScope scope(AccessRulesAuthorizer rules, Subject subject, ResourceType<?> op) {
        return rules.resolveScope(subject, op).toCompletableFuture().join();
    }

    private static boolean allowed(Authorizer authorizer, Subject subject, ResourceType<?> op, String name) {
        return authorizer.authorize(subject, List.of(new Action(op, name))).toCompletableFuture().join()
                .decision(op, name) == Decision.ALLOW;
    }

    /**
     * Registry decides as the Kroxylicious AclAuthorizer does on this fixture, apart from the
     * prefix-matching defect reported upstream, which the fixture does not exercise.
     */
    @Test
    void decisionsMatchTheKroxyliciousRulesService() throws Exception {
        // Through the production entry point, which builds the authorizer from AccessRules
        Authorizer ours = load();
        Authorizer theirs = kroxyliciousAuthorizer(RULES);
        List<String> mismatches = new ArrayList<>();
        for (Subject subject : SUBJECTS) {
            for (ResourceType<?> op : OPERATIONS) {
                for (String name : NAMES) {
                    if (allowed(ours, subject, op, name) != allowed(theirs, subject, op, name)) {
                        mismatches.add(subject.principals() + " " + op + " '" + name + "'");
                    }
                }
            }
        }
        assertEquals(List.of(), mismatches);
        assertEquals(theirs.supportedResourceTypes(), ours.supportedResourceTypes());
    }

    @Test
    void invalidFilesAreRejectedAsTheKroxyliciousRulesServiceRejectsThem() {
        String header = "from io.apicurio.authz import Artifact, Role;\n";
        String end = "otherwise deny;\n";
        for (String invalid : List.of(
                header + "allow Role with name = \"r\" to READ Artifact with name = \"x\";\n",
                header + "allow Role with name = \"r\" to READ Artifact with name = \"x\";\n"
                        + "deny Role with name = \"r\" to READ Artifact with name = \"y\";\n" + end,
                header + "allow Role with name = \"r\" to DELETE Artifact with name = \"x\";\n" + end,
                header + "allow Role with name = \"r\" to READ Artifact with name matching /(/;\n" + end,
                header + "allow Role with name = \"r\" to READ Artifact with name like \"a*b\";\n" + end,
                header + "allow Role with name matching /r/ to READ Artifact with name = \"x\";\n" + end,
                header + "allow Team with name = \"t\" to READ Artifact with name = \"x\";\n" + end,
                header + "allow Role with name = \"r\" to READ Nope with name = \"x\";\n" + end,
                "from io.apicurio.authz import Artifact, Role, Role;\n" + end,
                "from io.apicurio.authz import Artifact, Missing;\n"
                        + "allow User with name = \"u\" to READ Missing with name = \"x\";\n" + end,
                "from io.apicurio.authz import Artifact, Group;\n"
                        + "allow Group with name = \"u\" to READ Artifact with name = \"x\";\n" + end,
                "from io.apicurio.authz import Artifact, Role;\n"
                        + "allow User with name = \"u\" to READ Role with name = \"x\";\n" + end,
                // The grammar lexes a block comment as a regex literal, so it is a syntax error
                "/* comment */\n" + header + end)) {
            assertThrows(IllegalArgumentException.class, () -> AccessRules.parse(invalid), invalid);
            RuntimeException theirs = assertThrows(RuntimeException.class, () -> kroxyliciousAuthorizer(invalid),
                    invalid);
            assertFalse(theirs instanceof NullPointerException, invalid);
        }
    }

    /** The valid neighbours of the invalid files above are accepted by both. */
    @Test
    void validFilesAreAcceptedAsTheKroxyliciousRulesServiceAcceptsThem() throws Exception {
        String header = "from io.apicurio.authz import Artifact, Role;\n";
        String end = "otherwise deny;\n";
        for (String valid : List.of(
                end,
                header + "allow Role with name = \"r\" to READ Artifact with name = \"x\";\n" + end,
                header + "deny Role with name = \"r\" to READ Artifact with name = \"y\";\n"
                        + "allow Role with name = \"r\" to READ Artifact with name = \"x\";\n" + end,
                header + "allow Role with name = \"r\" to {READ, WRITE, ADMIN} Artifact with name = \"x\";\n" + end,
                header + "allow Role with name = \"r\" to READ Artifact with name matching /[(]/;\n" + end,
                header + "allow Role with name = \"r\" to READ Artifact with name like \"a\\*b\";\n" + end,
                header + "allow Role with name like \"r*\" to READ Artifact with name like \"a*\";\n" + end,
                "from io.apicurio.authz import Artifact as A, Role as R;\n"
                        + "allow R with name = \"r\" to READ A with name = \"x\";\n" + end,
                "// a comment\n" + header
                        + "allow User with name * to READ Artifact with name *;\n" + end)) {
            AccessRules.parse(valid);
            kroxyliciousAuthorizer(valid);
        }
    }

    @Test
    void scopeReportsPatternsAndShortCuts() throws Exception {
        AccessRulesAuthorizer rules = load();

        ResourceScope.Scoped bob = assertInstanceOf(ResourceScope.Scoped.class,
                scope(rules, subject(new User("bob"), new Role("team-a-dev")), Artifact.READ));
        assertEquals(Set.of("team-a/"), bob.allowPrefix());
        assertEquals(Set.of("team-a/secret"), bob.denyExact());
        // ADMIN implies READ, so the ADMIN pattern is part of the read scope
        assertEquals(Set.of("team-(b|c)/[a-z]+", "dot.only"), bob.allowPatterns());
        assertTrue(bob.hasPatterns());

        // The deny of WRITE on every group also denies READ, which WRITE implies
        assertInstanceOf(ResourceScope.Forbidden.class, scope(rules, subject(new Role("auditors")), Group.READ));
        ResourceScope.Scoped anyUser = assertInstanceOf(ResourceScope.Scoped.class,
                scope(rules, subject(new User("zed")), Group.READ));
        assertEquals(Set.of("public"), anyUser.allowPrefix());
        assertInstanceOf(ResourceScope.Unrestricted.class, scope(rules, subject(new Role("auditors")), Artifact.READ));

        assertInstanceOf(ResourceScope.Forbidden.class, scope(rules, Subject.anonymous(), Artifact.READ));
    }

    /**
     * The rules parser removes every backslash in a regex literal, so {@code /dot\.only/} is the
     * regex {@code dot.only}. Pinned so a change upstream is noticed: search must keep reading
     * patterns exactly as point access does.
     */
    @Test
    void regexEscapesAreReadAsTheRulesParserReadsThem() throws Exception {
        AccessRulesAuthorizer rules = load();
        Subject bob = subject(new User("bob"));
        ResourceScope scope = scope(rules, bob, Artifact.READ);
        for (String name : List.of("dot.only", "dotxonly")) {
            assertTrue(scope.matches(name), name);
            assertEquals(Decision.ALLOW, rules.authorize(bob, List.of(new Action(Artifact.READ, name)))
                    .toCompletableFuture().join().decision(Artifact.READ, name), name);
        }
    }
}
