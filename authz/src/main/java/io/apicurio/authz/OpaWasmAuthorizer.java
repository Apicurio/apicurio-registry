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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.styra.opa.wasm.OpaPolicy;
import com.styra.opa.wasm.OpaPolicyPool;

import io.kroxylicious.authorizer.service.Action;
import io.kroxylicious.authorizer.service.AuthorizeResult;
import io.kroxylicious.authorizer.service.Authorizer;
import io.kroxylicious.authorizer.service.ResourceType;
import io.kroxylicious.proxy.authentication.Subject;
import io.kroxylicious.proxy.authentication.User;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpaWasmAuthorizer implements Authorizer, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(OpaWasmAuthorizer.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DEFAULT_ENTRYPOINT = "registry/authz/allow";

    private final OpaPolicyPool policyPool;
    private volatile GrantsData grantsData;
    private final Map<Class<? extends ResourceType<?>>, String> resourceTypeNames;
    private final String entrypoint;

    private final Path dataFilePath;
    private volatile FileTime lastModified;

    private OpaWasmAuthorizer(OpaPolicyPool policyPool, GrantsData grantsData, Path dataFilePath,
            FileTime lastModified, Map<Class<? extends ResourceType<?>>, String> resourceTypeNames,
            String entrypoint) {
        this.policyPool = policyPool;
        this.grantsData = grantsData;
        this.dataFilePath = dataFilePath;
        this.lastModified = lastModified;
        this.resourceTypeNames = resourceTypeNames;
        this.entrypoint = entrypoint;
    }

    public static OpaWasmAuthorizer create(Path wasmPolicyPath, Path grantsFilePath, int poolSize)
            throws IOException {
        return create(wasmPolicyPath, grantsFilePath, poolSize, Map.of(), DEFAULT_ENTRYPOINT);
    }

    public static OpaWasmAuthorizer create(Path wasmPolicyPath, Path grantsFilePath, int poolSize,
            Map<Class<? extends ResourceType<?>>, String> resourceTypeNames) throws IOException {
        return create(wasmPolicyPath, grantsFilePath, poolSize, resourceTypeNames, DEFAULT_ENTRYPOINT);
    }

    public static OpaWasmAuthorizer create(Path wasmPolicyPath, Path grantsFilePath, int poolSize,
            Map<Class<? extends ResourceType<?>>, String> resourceTypeNames, String entrypoint)
            throws IOException {
        String grantsJson = "{}";
        FileTime lastMod = null;
        if (grantsFilePath != null && Files.exists(grantsFilePath)) {
            grantsJson = Files.readString(grantsFilePath);
            lastMod = Files.getLastModifiedTime(grantsFilePath);
        }

        OpaPolicyPool pool = OpaPolicyPool.create(
                () -> OpaPolicy.builder().withPolicy(wasmPolicyPath).build(), poolSize);

        return new OpaWasmAuthorizer(pool, GrantsData.parse(grantsJson), grantsFilePath, lastMod,
                new HashMap<>(resourceTypeNames), entrypoint);
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

        String userDataJson = data.getDataJsonForUser(user, roles);

        for (Action action : actions) {
            if (evaluateAction(user, roles, action, userDataJson)) {
                allowed.add(action);
            } else {
                denied.add(action);
            }
        }

        return CompletableFuture.completedStage(new AuthorizeResult(subject, allowed, denied));
    }

    @Override
    public Optional<Set<Class<? extends ResourceType<?>>>> supportedResourceTypes() {
        return Optional.empty();
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
        if (policyPool != null) {
            policyPool.close();
        }
    }

    private boolean evaluateAction(String user, Set<String> roles, Action action, String userDataJson) {
        String resourceType = resourceTypeNames.getOrDefault(action.resourceTypeClass(),
                action.resourceTypeClass().getSimpleName().toLowerCase(java.util.Locale.ROOT));
        String operation = action.operation().toString().toLowerCase(java.util.Locale.ROOT);
        String resourceName = action.resourceName();

        ObjectNode input = MAPPER.createObjectNode();
        input.put("user", user);
        ArrayNode rolesArray = input.putArray("roles");
        roles.forEach(rolesArray::add);
        input.put("operation", operation);
        input.put("resource_type", resourceType);
        input.put("resource_name", resourceName);

        try (OpaPolicyPool.Loan loan = policyPool.borrow()) {
            OpaPolicy policy = loan.policy();
            policy.data(userDataJson);
            policy.entrypoint(entrypoint);
            String result = policy.evaluate(input);
            JsonNode resultNode = MAPPER.readTree(result);
            boolean allowed = extractResult(resultNode);
            LOG.debug("Authorization: user={}, op={}, type={}, name={}, allowed={}",
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

    private static String extractUsername(Subject subject) {
        return subject.uniquePrincipalOfType(User.class)
                .map(User::name)
                .orElse("anonymous");
    }

    private static Set<String> extractRoles(Subject subject) {
        Set<String> roles = new HashSet<>();
        for (io.kroxylicious.proxy.authentication.Principal p : subject.principals()) {
            if (!(p instanceof User)) {
                roles.add(p.name());
            }
        }
        return roles;
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
