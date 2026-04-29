package io.apicurio.registry.auth.opawasm;

import java.util.List;

import io.apicurio.authz.GrantsData;
import io.apicurio.authz.GrantsAuthorizer;
import io.apicurio.authz.RolePrincipal;
import io.apicurio.registry.auth.AbstractAccessController;
import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedStyle;
import io.apicurio.registry.model.GroupId;
import io.apicurio.authz.Action;
import io.apicurio.authz.AuthorizeResult;
import io.apicurio.authz.Decision;
import io.apicurio.authz.Subject;
import io.apicurio.authz.User;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.interceptor.InvocationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class OpaWasmAccessController extends AbstractAccessController {

    private static final Logger LOG = LoggerFactory.getLogger(OpaWasmAccessController.class);
    private static final Logger AUDIT = LoggerFactory.getLogger("io.apicurio.registry.audit.authz");

    private static final AttributeKey<String> DECISION_KEY = AttributeKey.stringKey("decision");
    private static final AttributeKey<String> RESOURCE_TYPE_KEY = AttributeKey.stringKey("resource_type");
    private static final AttributeKey<String> OPERATION_KEY = AttributeKey.stringKey("operation");

    @Inject
    SecurityIdentity securityIdentity;

    private volatile GrantsAuthorizer authorizer;
    private LongCounter authzDecisionsCounter;

    @PostConstruct
    void initMetrics() {
        Meter meter = GlobalOpenTelemetry.getMeter("io.apicurio.registry");
        authzDecisionsCounter = meter.counterBuilder("apicurio.authz.decisions")
                .setDescription("Authorization decisions for per-resource access control")
                .setUnit("1")
                .build();
    }

    void setAuthorizer(GrantsAuthorizer authorizer) {
        this.authorizer = authorizer;
    }

    public GrantsAuthorizer getAuthorizer() {
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

        io.apicurio.authz.ResourceType<?> operation;
        String resourceType;
        boolean isGroupOnly = style == AuthorizedStyle.GroupOnly
                || (style == AuthorizedStyle.GroupAndArtifact
                    && (context.getParameters().length < 2
                        || !(context.getParameters()[1] instanceof String)));
        if (isGroupOnly) {
            operation = toGroupOp(level);
            resourceType = "group";
        } else {
            operation = toArtifactOp(level);
            resourceType = "artifact";
        }

        String operationName = level.name().toLowerCase(java.util.Locale.ROOT);
        Subject subject = buildSubject();

        AuthorizeResult result = authorizer.authorize(subject, List.of(new Action(operation, resourceName)))
                .toCompletableFuture().join();
        boolean allowed = result.decision(operation, resourceName) == Decision.ALLOW;

        String decisionStr = allowed ? "allow" : "deny";
        authzDecisionsCounter.add(1, Attributes.of(
                DECISION_KEY, decisionStr,
                RESOURCE_TYPE_KEY, resourceType,
                OPERATION_KEY, operationName));

        if (!allowed) {
            String user = getUsername();
            AUDIT.info("authz.denied user=\"{}\" operation=\"{}\" resource_type=\"{}\" resource=\"{}\"",
                    user, operationName, resourceType, resourceName);
        }

        return allowed;
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

    private String getUsername() {
        if (securityIdentity != null && !securityIdentity.isAnonymous()) {
            return securityIdentity.getPrincipal().getName();
        }
        return "anonymous";
    }

    private Subject buildSubject() {
        if (securityIdentity == null || securityIdentity.isAnonymous()) {
            return Subject.anonymous();
        }
        var principals = new java.util.HashSet<io.apicurio.authz.Principal>();
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
        Object[] params = context.getParameters();
        return switch (style) {
            case GroupAndArtifact -> {
                if (params.length < 2 || !(params[1] instanceof String)) {
                    yield getStringParam(context, 0);
                }
                yield buildResourceName(getStringParam(context, 0), getStringParam(context, 1));
            }
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
