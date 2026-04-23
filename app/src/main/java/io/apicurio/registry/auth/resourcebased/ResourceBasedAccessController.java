package io.apicurio.registry.auth.resourcebased;

import java.util.List;
import java.util.concurrent.CompletionStage;

import io.apicurio.registry.auth.AbstractAccessController;
import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedStyle;
import io.kroxylicious.authorizer.service.Action;
import io.kroxylicious.authorizer.service.AuthorizeResult;
import io.kroxylicious.authorizer.service.Authorizer;
import io.kroxylicious.authorizer.service.Decision;
import io.kroxylicious.proxy.authentication.Subject;
import io.kroxylicious.proxy.authentication.User;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.interceptor.InvocationContext;
import org.slf4j.Logger;

@Singleton
public class ResourceBasedAccessController extends AbstractAccessController {

    @Inject
    Logger log;

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    ResourceBasedAccessControllerConfig config;

    private Authorizer authorizer;

    void setAuthorizer(Authorizer authorizer) {
        this.authorizer = authorizer;
    }

    Authorizer getAuthorizer() {
        return authorizer;
    }

    @Override
    public boolean isAuthorized(InvocationContext context) {
        if (authorizer == null) {
            log.warn("Resource-based access controller has no authorizer configured, denying access.");
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

        Action action = buildAction(context, style, level);
        if (action == null) {
            return true;
        }

        Subject subject = buildSubject();
        CompletionStage<AuthorizeResult> resultStage = authorizer.authorize(subject, List.of(action));
        AuthorizeResult result = resultStage.toCompletableFuture().join();

        Decision decision = result.decision(action.operation(), action.resourceName());
        log.debug("Resource-based authorization: subject={}, action={}, decision={}",
                subject, action, decision);
        return decision == Decision.ALLOW;
    }

    private Subject buildSubject() {
        if (securityIdentity == null || securityIdentity.isAnonymous()) {
            return Subject.anonymous();
        }
        return new Subject(new User(securityIdentity.getPrincipal().getName()));
    }

    private Action buildAction(InvocationContext context, AuthorizedStyle style, AuthorizedLevel level) {
        String resourceName = extractResourceName(context, style);
        if (resourceName == null) {
            return null;
        }

        return switch (style) {
            case GroupAndArtifact, ArtifactOnly, GlobalId -> new Action(toArtifactOperation(level), resourceName);
            case GroupOnly -> new Action(toGroupOperation(level), resourceName);
            case None -> null;
        };
    }

    private String extractResourceName(InvocationContext context, AuthorizedStyle style) {
        return switch (style) {
            case GroupAndArtifact -> {
                String groupId = getStringParam(context, 0);
                String artifactId = getStringParam(context, 1);
                yield (groupId != null ? groupId : "default") + "/" + artifactId;
            }
            case GroupOnly -> getStringParam(context, 0);
            case ArtifactOnly -> getStringParam(context, 0);
            case GlobalId -> String.valueOf(getLongParam(context, 0));
            case None -> null;
        };
    }

    private static RegistryArtifact toArtifactOperation(AuthorizedLevel level) {
        return switch (level) {
            case Read -> RegistryArtifact.Read;
            case Write -> RegistryArtifact.Write;
            case Admin, AdminOrOwner -> RegistryArtifact.Admin;
            case None -> RegistryArtifact.Read;
        };
    }

    private static RegistryGroup toGroupOperation(AuthorizedLevel level) {
        return switch (level) {
            case Read -> RegistryGroup.Read;
            case Write -> RegistryGroup.Write;
            case Admin, AdminOrOwner -> RegistryGroup.Admin;
            case None -> RegistryGroup.Read;
        };
    }
}
