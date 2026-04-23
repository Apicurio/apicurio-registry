package io.apicurio.registry.auth.opawasm;

import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.styra.opa.wasm.OpaPolicy;
import com.styra.opa.wasm.OpaPolicyPool;

import io.apicurio.registry.auth.AbstractAccessController;
import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedStyle;
import io.apicurio.registry.model.GroupId;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.interceptor.InvocationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class OpaWasmAccessController extends AbstractAccessController {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Logger LOG = LoggerFactory.getLogger(OpaWasmAccessController.class);

    @Inject
    SecurityIdentity securityIdentity;

    private volatile OpaPolicyPool policyPool;
    private volatile GrantsData grantsData;

    void initialize(OpaPolicyPool policyPool, String permissionsData) {
        this.grantsData = GrantsData.parse(permissionsData);
        this.policyPool = policyPool;
    }

    void reloadData(String permissionsData) {
        this.grantsData = GrantsData.parse(permissionsData);
    }

    OpaPolicyPool getPolicyPool() {
        return policyPool;
    }

    GrantsData getGrantsData() {
        return grantsData;
    }

    @Override
    public boolean isAuthorized(InvocationContext context) {
        if (policyPool == null) {
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

        String user = securityIdentity != null && !securityIdentity.isAnonymous()
                ? securityIdentity.getPrincipal().getName()
                : "anonymous";

        Set<String> roles = securityIdentity != null ? securityIdentity.getRoles() : Set.of();
        String resourceType = (style == AuthorizedStyle.GroupOnly) ? "group" : "artifact";
        String operation = toOperationString(level);

        return evaluate(user, roles, operation, resourceType, resourceName);
    }

    public boolean evaluate(String user, Set<String> roles, String operation, String resourceType, String resourceName) {
        GrantsData data = this.grantsData;
        if (data == null) {
            LOG.error("Grants data not loaded, denying access.");
            return false;
        }

        ObjectNode input = MAPPER.createObjectNode();
        input.put("user", user);
        ArrayNode rolesArray = input.putArray("roles");
        roles.forEach(rolesArray::add);
        input.put("operation", operation);
        input.put("resource_type", resourceType);
        input.put("resource_name", resourceName);

        try (OpaPolicyPool.Loan loan = policyPool.borrow()) {
            OpaPolicy policy = loan.policy();
            policy.data(data.getRawJson());
            policy.entrypoint("registry/authz/allow");
            String result = policy.evaluate(input);
            JsonNode resultNode = MAPPER.readTree(result);
            boolean allowed = extractResult(resultNode);
            LOG.debug("OPA WASM authorization: user={}, op={}, type={}, name={}, allowed={}",
                    user, operation, resourceType, resourceName, allowed);
            return allowed;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.error("Interrupted while borrowing OPA policy from pool", e);
            return false;
        } catch (JsonProcessingException e) {
            LOG.error("Failed to parse OPA evaluation result", e);
            return false;
        }
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

    private static String toOperationString(AuthorizedLevel level) {
        return switch (level) {
            case Read -> "read";
            case Write -> "write";
            case Admin, AdminOrOwner -> "admin";
            case None -> "read";
        };
    }

    private static boolean extractResult(JsonNode resultNode) {
        if (resultNode.isArray() && !resultNode.isEmpty()) {
            JsonNode first = resultNode.get(0);
            if (first.has("result")) {
                return first.get("result").asBoolean(false);
            }
        }
        if (resultNode.isBoolean()) {
            return resultNode.asBoolean();
        }
        return false;
    }
}
