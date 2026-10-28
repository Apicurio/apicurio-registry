package io.apicurio.authz;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AccessRules} must read rules files exactly as Kroxylicious does: its authorizer must decide
 * as the one {@code AclAuthorizerService} builds, it must reject the same files, and its search
 * scope must select exactly the names that point access allows. Checked for every subject,
 * operation and name of a fixture covering every selector.
 */
class AccessRulesTest {

    private static final String RULES = """
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

    private static final List<Subject> SUBJECTS = List.of(
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

    private static final List<ResourceType<?>> OPERATIONS = List.of(
            Artifact.READ, Artifact.WRITE, Artifact.ADMIN, Group.READ, Group.WRITE, Group.ADMIN);

    private static final List<String> NAMES = List.of(
            "team-a/schema", "team-a/secret", "team-a/internal-x", "team-a/internal-", "team-a", "team-a/",
            "team-ab/x", "TEAM-A/schema", "team-b/abc", "team-b/ABC", "team-c/x", "team-d/x", "team-b/abc/d",
            "x/1", "x/2", "x/\"quoted\"", "team-a%2Fsub/y", "team-a/sub/y", "public", "public-group", "pub",
            "scratch/a", "scratch/a-locked", "scratch-locked", "a-locked", "literal", "literal2", "lit*star-x",
            "litXstar", "dot.only", "dotxonly", "svc/a", "svc", "", "%", "_");

    @TempDir
    Path dir;

    private static Subject subject(Principal... principals) {
        return new Subject(Set.of(principals));
    }

    private AccessRulesAuthorizer load() throws IOException {
        Path file = dir.resolve("rules.acl");
        Files.writeString(file, RULES);
        return AccessRulesAuthorizer.create(file, Set.of(Artifact.class, Group.class));
    }

    @Test
    void scopeMatchesPointAccessForEverySubjectOperationAndName() throws Exception {
        AccessRulesAuthorizer rules = load();
        List<String> mismatches = new ArrayList<>();
        int allowed = 0;
        int checked = 0;
        for (Subject subject : SUBJECTS) {
            for (ResourceType<?> op : OPERATIONS) {
                SearchFilterData scope = rules.scope(subject, op);
                for (String name : NAMES) {
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
        assertTrue(allowed > 50, "allowed: " + allowed);
        assertTrue(checked - allowed > 50, "denied: " + (checked - allowed));
    }

    /** The authorizer {@code AclAuthorizerService} builds from a file, as Kroxylicious does. */
    private Authorizer kroxyliciousAuthorizer(String rules) throws IOException {
        Path file = dir.resolve("kroxylicious.acl");
        Files.writeString(file, rules);
        AclAuthorizerService service = new AclAuthorizerService();
        service.initialize(new AclAuthorizerConfig(file.toString()));
        return service.build();
    }

    private static boolean allowed(Authorizer authorizer, Subject subject, ResourceType<?> op, String name) {
        return authorizer.authorize(subject, List.of(new Action(op, name))).toCompletableFuture().join()
                .decision(op, name) == Decision.ALLOW;
    }

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

        SearchFilterData bob = rules.scope(subject(new User("bob"), new Role("team-a-dev")), Artifact.READ);
        assertEquals(Set.of("team-a/"), bob.allowedPrefix());
        assertEquals(Set.of("team-a/secret"), bob.deniedExact());
        // ADMIN implies READ, so the ADMIN pattern is part of the read scope
        assertEquals(Set.of("team-(b|c)/[a-z]+", "dot.only"), bob.allowedPatterns());
        assertTrue(bob.hasPatterns());

        // The deny of WRITE on every group also denies READ, which WRITE implies
        SearchFilterData auditors = rules.scope(subject(new Role("auditors")), Group.READ);
        assertTrue(auditors.allowsNothing());
        assertFalse(auditors.hasPatterns());
        SearchFilterData anyUser = rules.scope(subject(new User("zed")), Group.READ);
        assertEquals(Set.of("public"), anyUser.allowedPrefix());
        assertFalse(anyUser.allowsEverything());
        assertTrue(rules.scope(subject(new Role("auditors")), Artifact.READ).allowsEverything());

        assertTrue(rules.scope(Subject.anonymous(), Artifact.READ).allowsNothing());
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
        SearchFilterData scope = rules.scope(bob, Artifact.READ);
        for (String name : List.of("dot.only", "dotxonly")) {
            assertTrue(scope.matches(name), name);
            assertEquals(Decision.ALLOW, rules.authorize(bob, List.of(new Action(Artifact.READ, name)))
                    .toCompletableFuture().join().decision(Artifact.READ, name), name);
        }
    }
}
