package io.apicurio.registry.webhooks;

import io.apicurio.registry.storage.impl.sql.SqlRegistryStorage;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.KafkasqlTestProfile;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Over HTTP, KafkaSQL storage answers every webhook operation with the documented 409 for an administrator,
 * and nothing is written to the local SQL replica.
 */
@QuarkusTest
@TestProfile(WebhookKafkaSqlCapabilityTest.Profile.class)
@Tag(ApicurioTestTags.SLOW)
public class WebhookKafkaSqlCapabilityTest {

    public static class Profile extends KafkasqlTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            Map<String, String> config = new HashMap<>(super.getConfigOverrides());
            config.putAll(WebhookAuthProfiles.basicAuth(Map.of(
                    "apicurio.auth.role-based-authorization", "true")));
            return config;
        }
    }

    @TestHTTPResource("/apis/registry/v3/admin/webhooks")
    URL webhooks;

    @Inject
    SqlRegistryStorage replica;

    @Test
    void everyOperationIsAConflictAndNothingIsWritten() {
        WebhookAuthTestSupport.assertEveryOperationReturns(webhooks,
                () -> given().auth().preemptive().basic("alice", "alice"), 409);
        assertEquals(0L, replica.searchWebhookSubscriptions(0, 20).getCount());
    }
}
