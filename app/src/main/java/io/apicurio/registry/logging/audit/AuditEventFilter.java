package io.apicurio.registry.logging.audit;

import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.rest.v3.beans.ArtifactMetaData;
import io.apicurio.registry.rest.v3.beans.CreateArtifactResponse;
import io.apicurio.registry.rest.v3.beans.GroupMetaData;
import io.apicurio.registry.rest.v3.beans.VersionMetaData;
import io.apicurio.registry.util.Priorities;
import io.apicurio.registry.utils.StringUtil;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_LOG;
import static io.apicurio.registry.utils.StringUtil.sanitizeForLog;

/**
 * The single emitter of {@link AuditEvent}s. The request side records the correlation id, the receive
 * time and the source of the request; the response side, which also runs for responses produced by
 * exception mappers and for 401/403 raised by the authorization interceptor, decides whether the request
 * is audited ({@link AuditedOperations#shouldEmit}) and fires exactly one event for it. Emitting per HTTP
 * request rather than per CDI call means that an endpoint delegating to another bean (ccompat v8 to v7)
 * still produces one event.
 */
@Provider
@Priority(Priorities.RequestResponseFilters.AUDIT)
@ApplicationScoped
public class AuditEventFilter implements ContainerRequestFilter, ContainerResponseFilter {

    public static final String AUDIT_ID_HEADER = "Audit-ID";

    static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";
    static final String USER_AGENT_HEADER = "User-Agent";

    private static final int MAX_FORWARDED_FOR_ENTRIES = 10;
    private static final int UNPROCESSABLE_ENTITY = 422;

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'").withZone(ZoneOffset.UTC);

    @ConfigProperty(name = "apicurio.audit.enabled", defaultValue = "true")
    @Info(category = CATEGORY_LOG, description = """
            Emit one audit event for every REST request that changes data, every request denied by \
            authentication or authorization, and every call to an explicitly audited read operation such as \
            data export. Each audited response carries the event's id in the `Audit-ID` header.""",
            availableSince = "3.4.0")
    boolean enabled;

    @Context
    HttpServletRequest request;

    @Context
    ResourceInfo resourceInfo;

    @Inject
    AuditContext auditContext;

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    BeanManager beanManager;

    @Inject
    Event<AuditEvent> events;

    @Inject
    Logger log;

    /**
     * What the filter needs to know about an endpoint, derived once from its definition.
     */
    private record Endpoint(AuditedOperations.Mode mode, String api, String resource, List<String> pathParams) {
    }

    private final Map<Method, Endpoint> endpoints = new ConcurrentHashMap<>();

    @Override
    public void filter(ContainerRequestContext requestContext) {
        if (enabled) {
            begin(requestContext);
        }
    }

    private void begin(ContainerRequestContext requestContext) {
        auditContext.begin(request.getRemoteAddr(), requestContext.getHeaderString(FORWARDED_FOR_HEADER),
                requestContext.getHeaderString(USER_AGENT_HEADER));
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        if (!enabled) {
            return;
        }
        Class<?> resourceClass = resourceInfo.getResourceClass();
        Method resourceMethod = resourceInfo.getResourceMethod();
        if (resourceClass == null || resourceMethod == null) {
            // No matched endpoint, so there is no operation to attribute the request to.
            return;
        }
        try {
            Endpoint endpoint = endpoints.computeIfAbsent(resourceMethod, m -> endpointOf(resourceClass, m));
            int status = responseContext.getStatus();
            if (!AuditedOperations.shouldEmit(requestContext.getMethod(), endpoint.mode(), status)) {
                return;
            }
            if (!auditContext.hasBegun()) {
                // A request rejected before the request filters ran still gets its event; the receive
                // time is then the time of the response.
                begin(requestContext);
            }
            if (!auditContext.markEmitted()) {
                return;
            }
            AuditEvent event = buildEvent(requestContext, endpoint, resourceMethod.getName(), status,
                    responseContext.getEntity());
            responseContext.getHeaders().putSingle(AUDIT_ID_HEADER, event.auditId());
            events.fire(event);
        } catch (RuntimeException e) {
            log.error("Failed to emit the audit event for operation {}", resourceMethod.getName(), e);
        }
    }

    private Endpoint endpointOf(Class<?> resourceClass, Method resourceMethod) {
        String template = AuditedOperations.pathTemplateOf(resourceClass, resourceMethod);
        return new Endpoint(AuditedOperations.modeOf(implementationOf(resourceClass, resourceMethod)),
                AuditedOperations.apiOf(template), AuditedOperations.resourceOf(template),
                AuditedOperations.pathParamsOf(template));
    }

    /**
     * {@link ResourceInfo} returns the generated JAX-RS interface method; the audit annotations are on the
     * implementing bean, so resolve it through CDI.
     */
    private Method implementationOf(Class<?> resourceClass, Method resourceMethod) {
        // AuditCoverageTest pins one implementation per resource; a second one would make resolve() throw
        // and stop every event, so that test fails first.
        Bean<?> bean = beanManager.resolve(beanManager.getBeans(resourceClass));
        Class<?> implementation = bean == null ? resourceClass : bean.getBeanClass();
        try {
            return implementation.getMethod(resourceMethod.getName(), resourceMethod.getParameterTypes());
        } catch (NoSuchMethodException e) {
            return resourceMethod;
        }
    }

    private AuditEvent buildEvent(ContainerRequestContext requestContext, Endpoint endpoint, String operation,
            int status, Object entity) {
        return new AuditEvent(auditContext.getAuditId(), traceId(),
                TIMESTAMP.format(auditContext.getRequestReceived()), TIMESTAMP.format(Instant.now()),
                requestContext.getMethod().toLowerCase(Locale.ROOT),
                sanitizeForLog(requestContext.getUriInfo().getRequestUri().getRawPath()), endpoint.api(),
                operation, user(), sourceIps(), sanitizeForLog(auditContext.getForwardedFor()),
                sanitizeForLog(auditContext.getUserAgent()),
                objectRef(endpoint, requestContext.getUriInfo().getPathParameters(), status, entity),
                new AuditEvent.ResponseStatus(status, reason(status)), AuditEvent.Outcome.of(status));
    }

    private static String traceId() {
        SpanContext spanContext = Span.current().getSpanContext();
        return spanContext.isValid() ? spanContext.getTraceId() : null;
    }

    private AuditEvent.User user() {
        try {
            if (!securityIdentity.isAnonymous()) {
                List<String> roles = securityIdentity.getRoles().stream().map(StringUtil::sanitizeForLog)
                        .sorted().toList();
                return new AuditEvent.User(sanitizeForLog(securityIdentity.getPrincipal().getName()), roles);
            }
        } catch (AuthenticationFailedException e) {
            // Authentication is lazy (quarkus.http.auth.proactive=false): when the credentials were
            // rejected, resolving the identity here fails again. The caller is then unauthenticated, and
            // the 401 must still be audited.
        }
        return new AuditEvent.User(AuditEvent.ANONYMOUS_USERNAME, List.of());
    }

    /**
     * Kubernetes ordering: the {@code X-Forwarded-For} entries first, then the address of the peer that
     * opened the connection. The forwarded entries are client-controlled and are not trusted.
     */
    private List<String> sourceIps() {
        List<String> ips = new ArrayList<>();
        String forwardedFor = auditContext.getForwardedFor();
        if (forwardedFor != null) {
            for (String entry : forwardedFor.split(",")) {
                if (ips.size() == MAX_FORWARDED_FOR_ENTRIES) {
                    break;
                }
                if (!entry.isBlank()) {
                    ips.add(sanitizeForLog(entry.trim()));
                }
            }
        }
        if (auditContext.getSourceIp() != null) {
            ips.add(sanitizeForLog(auditContext.getSourceIp()));
        }
        return ips;
    }

    private static AuditEvent.ObjectRef objectRef(Endpoint endpoint, MultivaluedMap<String, String> pathParams,
            int status, Object entity) {
        String groupId = null;
        String artifactId = null;
        String version = null;
        String namespace = null;
        String name = null;
        for (String param : endpoint.pathParams()) {
            String value = sanitizeForLog(pathParams.getFirst(param));
            switch (param) {
                case "groupId" -> groupId = value;
                case "artifactId" -> artifactId = value;
                case "version", "versionExpression" -> version = value;
                case "namespace" -> namespace = value;
                case "prefix" -> {
                    // The Iceberg catalog prefix, not part of the object's identity.
                }
                // No endpoint has two such parameters; the last one is the addressed object.
                default -> name = value;
            }
        }
        if (status < 300 && entity != null) {
            // A create names its object in the request body; take the identity the server confirmed
            // from typed fields of the response, never from the body or a toString().
            Identity created = Identity.of(entity);
            groupId = groupId != null ? groupId : sanitizeForLog(created.groupId());
            artifactId = artifactId != null ? artifactId : sanitizeForLog(created.artifactId());
            version = version != null ? version : sanitizeForLog(created.version());
        }
        return new AuditEvent.ObjectRef(endpoint.resource(), groupId, artifactId, version, namespace, name);
    }

    private record Identity(String groupId, String artifactId, String version) {

        private static final Identity NONE = new Identity(null, null, null);

        static Identity of(Object entity) {
            if (entity instanceof CreateArtifactResponse r) {
                Identity artifact = of(r.getArtifact());
                return new Identity(artifact.groupId(), artifact.artifactId(), of(r.getVersion()).version());
            }
            if (entity instanceof ArtifactMetaData a) {
                return new Identity(a.getGroupId(), a.getArtifactId(), null);
            }
            if (entity instanceof VersionMetaData v) {
                return new Identity(v.getGroupId(), v.getArtifactId(), v.getVersion());
            }
            if (entity instanceof GroupMetaData g) {
                return new Identity(g.getGroupId(), null, null);
            }
            // The v2 beans share their simple names with the v3 ones imported above.
            if (entity instanceof io.apicurio.registry.rest.v2.beans.ArtifactMetaData a) {
                return new Identity(a.getGroupId(), a.getId(), a.getVersion());
            }
            if (entity instanceof io.apicurio.registry.rest.v2.beans.VersionMetaData v) {
                return new Identity(v.getGroupId(), v.getId(), v.getVersion());
            }
            if (entity instanceof io.apicurio.registry.rest.v2.beans.GroupMetaData g) {
                return new Identity(g.getId(), null, null);
            }
            return NONE;
        }
    }

    /**
     * The standard reason for an error status, in the camel-case form Kubernetes uses (for example
     * {@code NotFound}). Derived from the status code only, never from an exception.
     */
    static String reason(int status) {
        if (status < 400) {
            return null;
        }
        if (status == UNPROCESSABLE_ENTITY) {
            // Used by the ccompat API, and absent from Response.Status in Jakarta REST 3.1.
            return "UnprocessableEntity";
        }
        Response.Status known = Response.Status.fromStatusCode(status);
        return known == null ? null : known.getReasonPhrase().replace(" ", "").replace("-", "");
    }
}
