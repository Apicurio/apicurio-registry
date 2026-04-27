package io.apicurio.registry.auth.opawasm;

import java.util.List;
import java.util.Set;

import io.apicurio.authz.GrantsData;
import io.apicurio.authz.OpaWasmAuthorizer;
import io.apicurio.authz.RolePrincipal;
import io.apicurio.registry.auth.AbstractAccessController;
import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedStyle;
import io.apicurio.registry.model.GroupId;
import io.kroxylicious.authorizer.service.Action;
import io.kroxylicious.authorizer.service.AuthorizeResult;
import io.kroxylicious.authorizer.service.Decision;
import io.kroxylicious.proxy.authentication.Subject;
import io.kroxylicious.proxy.authentication.User;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.interceptor.InvocationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class OpaWasmAccessController extends AbstractAccessController {

    private static final Logger LOG = LoggerFactory.getLogger(OpaWasmAccessController.class);

    @Inject
    SecurityIdentity securityIdentity;

    private volatile OpaWasmAuthorizer authorizer;

    void setAuthorizer(OpaWasmAuthorizer authorizer) {
        this.authorizer = authorizer;
    }

    OpaWasmAuthorizer getAuthorizer() {
        return authorizer;
    }

    GrantsData getGrantsData() {
        return authorizer != null ? authorizer.getGrantsData() : null;
    }

    @Override
    public boolean isAuthorized(InvocationContext context) {
        if (authorizer == null) {
            LOG.error("OPA WASM access controller not initialized, denying access.");
            return false;
        }

        Authorized annotation = context.getMethod().getAnnotation(Authorized.class);
        if (annotation == null) {
            return true;
        }

        AuthorizedStyle style = annotation.style();
        AuthorizedLevel level = annotation.level();

        if (style == AuthorizedStyle.None || level == AuthorizedLevel.None) {
            return true;
        }

        String resourceName = extractResourceName(context, style);
        if (resourceName == null) {
            return true;
        }

        io.kroxylicious.authorizer.service.ResourceType<?> operation;
        if (style == AuthorizedStyle.GroupOnly) {
            operation = toGroupOp(level);
        } else {
            operation = toArtifactOp(level);
        }
        Subject subject = buildSubject();

        AuthorizeResult result = authorizer.authorize(subject, List.of(new Action(operation, resourceName)))
                .toCompletableFuture().join();
        return result.decision(operation, resourceName) == Decision.ALLOW;
    }

    public boolean canReadArtifact(String groupId, String artifactId) {
        if (authorizer == null) {
            return false;
        }
        String resourceName = buildResourceName(groupId, artifactId);
        Subject subject = buildSubject();
        AuthorizeResult result = authorizer.authorize(subject,
                List.of(new Action(RegistryResourceType.Artifact.Read, resourceName)))
                .toCompletableFuture().join();
        return result.decision(RegistryResourceType.Artifact.Read, resourceName) == Decision.ALLOW;
    }

    private Subject buildSubject() {
        if (securityIdentity == null || securityIdentity.isAnonymous()) {
            return Subject.anonymous();
        }
        var principals = new java.util.HashSet<io.kroxylicious.proxy.authentication.Principal>();
        principals.add(new User(securityIdentity.getPrincipal().getName()));
        for (String role : securityIdentity.getRoles()) {
            principals.add(new RolePrincipal(role));
        }
        return new Subject(principals);
    }

    public static String buildResourceName(String groupId, String artifactId) {
        String normalizedGroup = groupId != null ? new GroupId(groupId).getRawGroupIdWithNull() : "default";
        if (normalizedGroup == null) {
            normalizedGroup = "default";
        }
        return normalizedGroup + "/" + artifactId;
    }

    private String extractResourceName(InvocationContext context, AuthorizedStyle style) {
        return switch (style) {
            case GroupAndArtifact -> buildResourceName(getStringParam(context, 0), getStringParam(context, 1));
            case GroupOnly -> getStringParam(context, 0);
            case ArtifactOnly -> getStringParam(context, 0);
            case GlobalId -> String.valueOf(getLongParam(context, 0));
            case None -> null;
        };
    }

    private static RegistryResourceType.Artifact toArtifactOp(AuthorizedLevel level) {
        return switch (level) {
            case Read -> RegistryResourceType.Artifact.Read;
            case Write -> RegistryResourceType.Artifact.Write;
            case Admin, AdminOrOwner -> RegistryResourceType.Artifact.Admin;
            case None -> RegistryResourceType.Artifact.Read;
        };
    }

    private static RegistryResourceType.Group toGroupOp(AuthorizedLevel level) {
        return switch (level) {
            case Read -> RegistryResourceType.Group.Read;
            case Write -> RegistryResourceType.Group.Write;
            case Admin, AdminOrOwner -> RegistryResourceType.Group.Admin;
            case None -> RegistryResourceType.Group.Read;
        };
    }
}
