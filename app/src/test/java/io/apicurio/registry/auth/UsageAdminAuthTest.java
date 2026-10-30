package io.apicurio.registry.auth;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.BasicAuthWithPropertiesTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The usage endpoints expose which applications consume which schemas, so they are admin only. Every one
 * of them must refuse a non-admin caller and answer an admin.
 */
@QuarkusTest
@TestProfile(UsageAdminAuthTest.Profile.class)
@Tag(ApicurioTestTags.SLOW)
public class UsageAdminAuthTest extends AbstractResourceTestBase {

    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            Map<String, String> config = new HashMap<>(
                    new BasicAuthWithPropertiesTestProfile().getConfigOverrides());
            config.put("apicurio.features.experimental.enabled", "true");
            config.put("apicurio.usage.telemetry.enabled", "true");
            return config;
        }
    }

    private static final Map<String, Function<RegistryClient, Object>> ENDPOINTS = Map.of(
            "summary", c -> c.admin().usage().summary().get(),
            "artifact metrics", c -> c.admin().usage().artifacts().byGroupId("g").byArtifactId("a").get(),
            "heatmap", c -> c.admin().usage().artifacts().byGroupId("g").byArtifactId("a").heatmap().get(),
            "deprecation readiness", c -> c.admin().usage().artifacts().byGroupId("g").byArtifactId("a")
                    .versions().byVersion("1").deprecationReadiness().get());

    @Override
    protected RegistryClient createRestClientV3(Vertx vertx) {
        return clientFor(BasicAuthWithPropertiesTest.ADMIN_USERNAME, BasicAuthWithPropertiesTest.ADMIN_PASSWORD);
    }

    @Test
    public void adminSeesEmptyResultsForAnArtifactNobodyFetched() {
        Assertions.assertEquals(0, clientV3.admin().usage().summary().get().getActive(),
                "no usage events exist in this profile, so no version is active");
        Assertions.assertEquals(List.of(),
                clientV3.admin().usage().artifacts().byGroupId("g").byArtifactId("a").get().getVersions());
        Assertions.assertEquals(List.of(), clientV3.admin().usage().artifacts().byGroupId("g")
                .byArtifactId("a").heatmap().get().getConsumers());
        var readiness = clientV3.admin().usage().artifacts().byGroupId("g").byArtifactId("a").versions()
                .byVersion("1").deprecationReadiness().get();
        Assertions.assertEquals(List.of(), readiness.getActiveConsumers());
        Assertions.assertTrue(readiness.getSafeToDeprecate());
    }

    @Test
    public void developerAndReadOnlyAreForbiddenOnEveryUsageEndpoint() {
        Map<String, String> nonAdmins = Map.of(
                BasicAuthWithPropertiesTest.DEVELOPER_USERNAME, BasicAuthWithPropertiesTest.DEVELOPER_PASSWORD,
                BasicAuthWithPropertiesTest.READONLY_USERNAME, BasicAuthWithPropertiesTest.READONLY_PASSWORD);
        nonAdmins.forEach((user, password) -> {
            RegistryClient client = clientFor(user, password);
            ENDPOINTS.forEach((name, call) -> {
                Exception error = Assertions.assertThrows(Exception.class, () -> call.apply(client),
                        user + " on " + name);
                assertForbidden(error);
            });
        });
    }

    private RegistryClient clientFor(String user, String password) {
        return RegistryClientFactory.create(RegistryClientOptions.create().registryUrl(registryV3ApiUrl)
                .vertx(vertx).basicAuth(user, password));
    }
}
