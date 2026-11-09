package io.apicurio.authz;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.apicurio.scope.ResourceScope;
import io.apicurio.scope.ResourceScopeProvider;
import io.kroxylicious.authorizer.provider.acl.AclAuthorizer;
import io.kroxylicious.authorizer.provider.acl.AclAuthorizerService;
import io.kroxylicious.authorizer.provider.acl.InvalidRulesFileException;
import io.kroxylicious.authorizer.provider.acl.ResourceNameSelectors;
import io.kroxylicious.authorizer.provider.acl.ResourceSelectors;
import io.kroxylicious.authorizer.provider.acl.parser.AclRulesLexer;
import io.kroxylicious.authorizer.provider.acl.parser.AclRulesParser;
import io.kroxylicious.authorizer.service.Action;
import io.kroxylicious.authorizer.service.AuthorizeResult;
import io.kroxylicious.authorizer.service.Authorizer;
import io.kroxylicious.authorizer.service.ResourceType;
import io.kroxylicious.identity.Identity;
import io.kroxylicious.identity.Subject;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Authorizes against an access rules file in the Kroxylicious ACL rules format (see Kroxylicious
 * proposal 009), parsed and evaluated by the Kroxylicious {@link AclAuthorizer}.
 *
 * <p>On top of the rules, this class:</p>
 * <ul>
 *     <li>rejects a file that refers to resource types this system does not enforce, so a rule that
 *     looks like an access control cannot silently be ignored;</li>
 *     <li>reloads the file when it changes. A file that cannot be loaded leaves the previous rules
 *     in effect, so a bad edit never locks everyone out;</li>
 *     <li>provides the scope of the rules for search pre-filtering, as a
 *     {@link ResourceScopeProvider}, from the same version of the file as the point decisions.</li>
 * </ul>
 */
public final class AccessRulesAuthorizer implements Authorizer, ResourceScopeProvider {

    private static final Logger LOG = LoggerFactory.getLogger(AccessRulesAuthorizer.class);

    private final Path rulesFile;
    private final Set<Class<? extends ResourceType<?>>> supportedResourceTypes;
    /** The authorizer of one version of the file, replaced as a whole on reload. */
    private record Rules(AclAuthorizer authorizer) {
    }

    private volatile Rules rules;
    private volatile FileTime lastModified;

    private AccessRulesAuthorizer(Path rulesFile, Set<Class<? extends ResourceType<?>>> supportedResourceTypes,
            Rules rules, FileTime lastModified) {
        this.rulesFile = rulesFile;
        this.supportedResourceTypes = supportedResourceTypes;
        this.rules = rules;
        this.lastModified = lastModified;
    }

    /**
     * Loads the rules file.
     *
     * @param supportedResourceTypes the resource types this system enforces; a file referring to
     *        any other type is rejected
     * @throws IOException if the file does not exist or cannot be read
     * @throws IllegalArgumentException if the file is not a valid rules file for these types
     */
    public static AccessRulesAuthorizer create(Path rulesFile,
            Set<Class<? extends ResourceType<?>>> supportedResourceTypes) throws IOException {
        if (!Files.isRegularFile(rulesFile)) {
            throw new IOException("Access rules file not found: " + rulesFile);
        }
        FileTime lastModified = Files.getLastModifiedTime(rulesFile);
        Set<Class<? extends ResourceType<?>>> supported = Set.copyOf(supportedResourceTypes);
        Rules rules = load(Files.readString(rulesFile), supported);
        return new AccessRulesAuthorizer(rulesFile, supported, rules, lastModified);
    }

    /**
     * Builds the rules from the file content. The point decisions and the scope come from the same
     * authorizer, built from one parse of one version of the file.
     */
    private static Rules load(String content, Set<Class<? extends ResourceType<?>>> supported) {
        AclAuthorizer authorizer = parse(content);
        Set<Class<? extends ResourceType<?>>> unsupported = new HashSet<>(
                authorizer.supportedResourceTypes().orElse(Set.of()));
        unsupported.removeAll(supported);
        if (!unsupported.isEmpty()) {
            // Fail closed: a rule for a type nobody enforces would look like an access control
            throw new IllegalArgumentException("Access rules file refers to resource types that are not "
                    + "enforced here: " + unsupported.stream().map(Class::getName).sorted().toList());
        }
        return new Rules(authorizer);
    }

    /**
     * Parses a rules file, rejecting content after {@code otherwise deny;}.
     *
     * @throws IllegalArgumentException if the content is not a valid rules file
     */
    static AclAuthorizer parse(String content) {
        rejectContentAfterOtherwiseDeny(content);
        try {
            // Resolve the imported classes in Registry's class loader: in Quarkus dev and test
            // modes it is not the Kroxylicious module's own
            return AclAuthorizerService.parse(content, AccessRulesAuthorizer.class.getClassLoader());
        } catch (InvalidRulesFileException e) {
            throw new IllegalArgumentException("Invalid access rules file: " + e.getMessage(), e);
        }
    }

    /**
     * The grammar ends at {@code otherwise deny;} without requiring the end of the input, so the
     * Kroxylicious parser drops anything after it silently: a deny rule appended there would never
     * apply. Syntax errors before that point are left to the Kroxylicious parser to report.
     */
    private static void rejectContentAfterOtherwiseDeny(String content) {
        AclRulesLexer lexer = new AclRulesLexer(CharStreams.fromString(content));
        AclRulesParser parser = new AclRulesParser(new CommonTokenStream(lexer));
        lexer.removeErrorListeners();
        parser.removeErrorListeners();
        parser.rule_();
        if (parser.getNumberOfSyntaxErrors() > 0) {
            return;
        }
        Token trailing = parser.getCurrentToken();
        if (trailing.getType() != Token.EOF) {
            throw new IllegalArgumentException("Invalid access rules file: " + trailing.getLine() + ":"
                    + trailing.getCharPositionInLine() + ": unexpected '" + trailing.getText()
                    + "' after 'otherwise deny;'. Every rule must come before it.");
        }
    }

    @Override
    public CompletionStage<AuthorizeResult> authorize(Identity subject, List<Action> actions) {
        return rules.authorizer().authorize(subject, actions);
    }

    /**
     * @return the names of {@code operation}'s resource type that the rules allow {@code subject}
     *         to perform {@code operation} on, exactly as {@link #authorize} decides them; forbidden
     *         for a resource type this authorizer does not enforce
     */
    @Override
    public CompletionStage<ResourceScope> resolveScope(Subject subject, ResourceType<?> operation) {
        if (!supportedResourceTypes.contains(((Enum<?>) operation).getDeclaringClass())) {
            return CompletableFuture.completedStage(ResourceScope.forbidden());
        }
        ResourceSelectors selectors = rules.authorizer().resourceSelectors(subject, operation);
        ResourceNameSelectors allowed = selectors.allowed();
        ResourceNameSelectors denied = selectors.denied();
        Set<String> denyPrefix = new HashSet<>(denied.prefixes());
        if (denied.anyName()) {
            // A deny of every name is the empty deny prefix
            denyPrefix.add("");
        }
        return CompletableFuture.completedStage(ResourceScope.of(allowed.anyName(), allowed.names(), allowed.prefixes(),
                allowed.patterns(), denied.names(), denyPrefix, denied.patterns()));
    }

    @Override
    public Optional<Set<Class<? extends ResourceType<?>>>> supportedResourceTypes() {
        return Optional.of(supportedResourceTypes);
    }

    /**
     * @return the modification time of the last file version processed, whether it was loaded or
     *         rejected
     */
    public FileTime getLastSeenModified() {
        return lastModified;
    }

    /**
     * Reloads the rules file if its modification time changed. If the new file is missing or
     * invalid, the previous rules stay in effect and an error is logged once per change.
     *
     * @return true if new rules were loaded
     */
    public boolean checkForChanges() {
        FileTime currentModified;
        try {
            if (!Files.isRegularFile(rulesFile)) {
                return false;
            }
            currentModified = Files.getLastModifiedTime(rulesFile);
        } catch (IOException e) {
            LOG.error("Failed to check access rules file {}: {}", rulesFile, e.getMessage());
            return false;
        }
        if (currentModified.equals(lastModified)) {
            return false;
        }
        try {
            LOG.info("Access rules file changed, reloading: {}", rulesFile);
            this.rules = load(Files.readString(rulesFile), supportedResourceTypes);
            LOG.info("Access rules reloaded successfully.");
            return true;
        } catch (IOException | IllegalArgumentException e) {
            LOG.error("Failed to reload access rules file {}; keeping the previous rules: {}", rulesFile,
                    e.getMessage());
            return false;
        } finally {
            // Recorded whether the version was loaded or rejected, so a bad file is reported once;
            // and only once processing is complete, so this never reports a version whose rules are
            // not in effect yet
            this.lastModified = currentModified;
        }
    }
}
