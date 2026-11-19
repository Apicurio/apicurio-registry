package io.apicurio.registry.auth;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Listing and fetching GitOps validation tasks (#11188) must require Admin, like creating and
 * deleting one, not just Read: with #10836, a GET for a task that the serving replica doesn't hold
 * decodes the ID and runs the fetch and validation there, so a Read-level caller who can list task
 * IDs could make every replica fetch the repository and validate it.
 */
@QuarkusTest
@TestProfile(GitOpsValidateAdminAuthTest.Profile.class)
@Tag(ApicurioTestTags.SLOW)
public class GitOpsValidateAdminAuthTest extends AbstractResourceTestBase {

    private static final String ADMIN_USER = "alice";
    private static final String ADMIN_PASSWORD = "alice";
    private static final String READONLY_USER = "duncan";
    private static final String READONLY_PASSWORD = "duncan";

    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            Map<String, String> config = new HashMap<>();
            config.put("quarkus.oidc.tenant-enabled", "false");
            config.put("quarkus.http.auth.basic", "true");
            config.put("apicurio.auth.role-based-authorization", "true");
            config.put("apicurio.auth.anonymous-read-access.enabled", "true");
            config.put("quarkus.security.users.embedded.enabled", "true");
            config.put("quarkus.security.users.embedded.plain-text", "true");
            config.put("quarkus.security.users.embedded.users.alice", "alice");
            config.put("quarkus.security.users.embedded.users.duncan", "duncan");
            config.put("quarkus.security.users.embedded.roles.alice", "sr-admin");
            config.put("quarkus.security.users.embedded.roles.duncan", "sr-readonly");
            return config;
        }
    }

    /**
     * GitOps storage is not enabled in this profile (auth is checked before the handler looks at
     * storage), so an authorized caller reaches the business logic and gets 409; the point of
     * this test is only whether RBAC lets the call through at all.
     */
    private static final Map<String, Function<RegistryClient, Object>> ENDPOINTS = Map.of(
            "list", c -> c.admin().gitops().validate().get(),
            "get", c -> c.admin().gitops().validate().byTaskId("anytask").get());

    @Override
    protected RegistryClient createRestClientV3(Vertx vertx) {
        return clientFor(ADMIN_USER, ADMIN_PASSWORD);
    }

    @Test
    public void adminReachesTheHandlerOnEveryValidateEndpoint() {
        ENDPOINTS.forEach((name, call) -> {
            Exception error = Assertions.assertThrows(Exception.class, () -> call.apply(clientV3), name);
            // 409: GitOps storage is not enabled in this profile, i.e. RBAC let the admin through
            assertConflict(error);
        });
    }

    @Test
    public void readOnlyIsForbiddenOnEveryValidateEndpoint() {
        RegistryClient client = clientFor(READONLY_USER, READONLY_PASSWORD);
        ENDPOINTS.forEach((name, call) -> {
            Exception error = Assertions.assertThrows(Exception.class, () -> call.apply(client), name);
            assertForbidden(error);
        });
    }

    /**
     * An anonymous caller sends no credentials at all, so basic auth rejects the request with 401
     * before RBAC is even evaluated — the standard "no credentials" response, distinct from the 403
     * a known-but-unauthorized user gets. Anonymous read access does not change this: it only grants
     * the {@code $anonymous} role for Read-level operations, and these are Admin-level.
     */
    @Test
    public void anonymousGetsNoCredentialsOnEveryValidateEndpointDespiteAnonymousReadAccess() {
        RegistryClient client = RegistryClientFactory.create(
                RegistryClientOptions.create().registryUrl(registryV3ApiUrl).vertx(vertx));
        ENDPOINTS.forEach((name, call) -> {
            Exception error = Assertions.assertThrows(Exception.class, () -> call.apply(client), name);
            assertUnauthorized(error);
        });
    }

    private void assertUnauthorized(Exception exception) {
        if (exception instanceof com.microsoft.kiota.ApiException apiException) {
            Assertions.assertEquals(401, apiException.getResponseStatusCode());
        } else {
            Assertions.fail("Expected a 401 Unauthorized, got: " + exception, exception);
        }
    }

    private void assertConflict(Exception exception) {
        if (exception instanceof com.microsoft.kiota.ApiException apiException) {
            Assertions.assertEquals(409, apiException.getResponseStatusCode());
        } else {
            Assertions.fail("Expected a 409 Conflict, got: " + exception, exception);
        }
    }

    private RegistryClient clientFor(String user, String password) {
        return RegistryClientFactory.create(RegistryClientOptions.create().registryUrl(registryV3ApiUrl)
                .vertx(vertx).basicAuth(user, password));
    }
}
