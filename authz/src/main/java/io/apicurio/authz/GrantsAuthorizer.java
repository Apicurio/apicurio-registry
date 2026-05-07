package io.apicurio.authz;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GrantsAuthorizer implements Authorizer, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(GrantsAuthorizer.class);

    private volatile GrantsData grantsData;
    private final Map<Class<? extends ResourceType<?>>, String> resourceTypeNames;

    private final Path dataFilePath;
    private volatile FileTime lastModified;

    private GrantsAuthorizer(GrantsData grantsData, Path dataFilePath, FileTime lastModified,
            Map<Class<? extends ResourceType<?>>, String> resourceTypeNames) {
        this.grantsData = grantsData;
        this.dataFilePath = dataFilePath;
        this.lastModified = lastModified;
        this.resourceTypeNames = resourceTypeNames;
    }

    public static GrantsAuthorizer create(Path grantsFilePath) throws IOException {
        return create(grantsFilePath, Map.of());
    }

    public static GrantsAuthorizer create(Path grantsFilePath,
            Map<Class<? extends ResourceType<?>>, String> resourceTypeNames) throws IOException {
        String grantsJson = "{}";
        FileTime lastMod = null;
        if (grantsFilePath != null && Files.exists(grantsFilePath)) {
            grantsJson = Files.readString(grantsFilePath);
            lastMod = Files.getLastModifiedTime(grantsFilePath);
        }

        return new GrantsAuthorizer(GrantsData.parse(grantsJson), grantsFilePath, lastMod,
                new HashMap<>(resourceTypeNames));
    }

    public GrantsData getGrantsData() {
        return grantsData;
    }

    @Override
    public CompletionStage<AuthorizeResult> authorize(Subject subject, List<Action> actions) {
        String user = extractUsername(subject);
        Set<String> roles = extractRoles(subject);
        GrantsData data = this.grantsData;

        List<Action> allowed = new ArrayList<>();
        List<Action> denied = new ArrayList<>();

        if (data == null) {
            denied.addAll(actions);
            return CompletableFuture.completedStage(new AuthorizeResult(subject, allowed, denied));
        }

        if (data.isAdmin(roles)) {
            allowed.addAll(actions);
            return CompletableFuture.completedStage(new AuthorizeResult(subject, allowed, denied));
        }

        List<Grant> userGrants = data.getGrantsForUser(user, roles);

        for (Action action : actions) {
            if (matchesAnyGrant(userGrants, action)) {
                allowed.add(action);
            } else {
                denied.add(action);
            }
        }

        return CompletableFuture.completedStage(new AuthorizeResult(subject, allowed, denied));
    }

    public boolean checkForDataFileChanges() {
        if (dataFilePath == null) {
            return false;
        }
        try {
            if (!Files.exists(dataFilePath)) {
                return false;
            }
            FileTime currentModified = Files.getLastModifiedTime(dataFilePath);
            if (lastModified != null && currentModified.compareTo(lastModified) > 0) {
                LOG.info("Grants data file changed, reloading: {}", dataFilePath);
                String json = Files.readString(dataFilePath);
                this.grantsData = GrantsData.parse(json);
                this.lastModified = currentModified;
                LOG.info("Grants data reloaded successfully.");
                return true;
            }
        } catch (IOException e) {
            LOG.error("Failed to check or reload grants data file: {}", dataFilePath, e);
        }
        return false;
    }

    @Override
    public void close() {
    }

    private boolean matchesAnyGrant(List<Grant> userGrants, Action action) {
        String resourceType = resourceTypeNames.getOrDefault(action.resourceTypeClass(),
                action.resourceTypeClass().getSimpleName().toLowerCase(java.util.Locale.ROOT));
        String operation = action.operation().toString().toLowerCase(java.util.Locale.ROOT);
        String resourceName = action.resourceName();

        // Deny rules take precedence — check them first
        for (Grant grant : userGrants) {
            if (!grant.deny()) {
                continue;
            }
            if (!grant.matchesResourceType(resourceType)) {
                continue;
            }
            if (!grant.impliesOperation(operation)) {
                continue;
            }
            if (grant.matchesResource(resourceName)) {
                return false;
            }
        }

        // Then check allow rules
        for (Grant grant : userGrants) {
            if (grant.deny()) {
                continue;
            }
            if (!grant.matchesResourceType(resourceType)) {
                continue;
            }
            if (!grant.impliesOperation(operation)) {
                continue;
            }
            if (grant.matchesResource(resourceName)) {
                return true;
            }
        }
        return false;
    }

    private static String extractUsername(Subject subject) {
        return subject.principalOfType(User.class)
                .map(User::name)
                .orElse("anonymous");
    }

    private static Set<String> extractRoles(Subject subject) {
        Set<String> roles = new HashSet<>();
        for (io.apicurio.authz.Principal p : subject.principals()) {
            if (!(p instanceof User)) {
                roles.add(p.name());
            }
        }
        return roles;
    }
}
