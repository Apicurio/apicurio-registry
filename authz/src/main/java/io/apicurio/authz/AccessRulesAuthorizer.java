package io.apicurio.authz;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;

import io.kroxylicious.authorizer.provider.acl.AclAuthorizerConfig;
import io.kroxylicious.authorizer.provider.acl.AclAuthorizerService;
import io.kroxylicious.authorizer.service.Action;
import io.kroxylicious.authorizer.service.AuthorizeResult;
import io.kroxylicious.authorizer.service.Authorizer;
import io.kroxylicious.authorizer.service.AuthorizerException;
import io.kroxylicious.authorizer.service.ResourceType;
import io.kroxylicious.identity.Identity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Authorizes against an access rules file in the Kroxylicious ACL rules format (see Kroxylicious
 * proposal 009), using the Kroxylicious {@code AclAuthorizer}.
 *
 * <p>On top of {@code AclAuthorizer}, this class:</p>
 * <ul>
 *     <li>rejects a file that refers to resource types this system does not enforce, so a rule that
 *     looks like an access control cannot silently be ignored;</li>
 *     <li>reloads the file when it changes. A file that cannot be loaded leaves the previous rules
 *     in effect, so a bad edit never locks everyone out;</li>
 *     <li>provides the scope of the rules for search pre-filtering ({@link #scope}), from the same
 *     version of the file as the point decisions.</li>
 * </ul>
 */
public final class AccessRulesAuthorizer implements Authorizer {

    private static final Logger LOG = LoggerFactory.getLogger(AccessRulesAuthorizer.class);

    private final Path rulesFile;
    private final Set<Class<? extends ResourceType<?>>> supportedResourceTypes;
    /** The authorizer and scope of one version of the file, replaced together on reload. */
    private record Rules(Authorizer authorizer, AccessRulesScope scope) {
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
     * Builds the rules from the file content. The content is read once and loaded from a private
     * copy, so the rules always come from one consistent version of the file.
     */
    private static Rules load(String content, Set<Class<? extends ResourceType<?>>> supported) throws IOException {
        Path copy = Files.createTempFile("access-rules", ".acl");
        try {
            Files.writeString(copy, content);
            AclAuthorizerService service = new AclAuthorizerService();
            Authorizer authorizer;
            try {
                service.initialize(new AclAuthorizerConfig(copy.toString()));
                authorizer = service.build();
            } catch (AuthorizerException e) {
                throw new IllegalArgumentException("Invalid access rules file: " + e.getMessage(), e);
            }
            Set<Class<? extends ResourceType<?>>> unsupported = new HashSet<>(
                    authorizer.supportedResourceTypes().orElse(Set.of()));
            unsupported.removeAll(supported);
            if (!unsupported.isEmpty()) {
                // Fail closed: a rule for a type nobody enforces would look like an access control
                throw new IllegalArgumentException("Access rules file refers to resource types that are not "
                        + "enforced here: " + unsupported.stream().map(Class::getName).sorted().toList());
            }
            // Parsed after AclAuthorizer has validated the same content
            return new Rules(authorizer, AccessRulesScope.parse(content));
        } finally {
            Files.deleteIfExists(copy);
        }
    }

    @Override
    public CompletionStage<AuthorizeResult> authorize(Identity subject, List<Action> actions) {
        return rules.authorizer().authorize(subject, actions);
    }

    /**
     * @return the names of {@code operation}'s resource type that the rules allow {@code subject}
     *         to perform {@code operation} on, exactly as {@link #authorize} decides them
     */
    public SearchFilterData scope(Identity subject, ResourceType<?> operation) {
        return rules.scope().scope(subject, operation);
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
