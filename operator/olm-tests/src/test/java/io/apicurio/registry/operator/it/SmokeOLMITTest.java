package io.apicurio.registry.operator.it;

import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class SmokeOLMITTest extends OLMITBase {

    @Test
    void smoke() {
        // Wait for the operator to deploy
        var projectVersion = ConfigProvider.getConfig().getValue(PROJECT_VERSION_PROP, String.class);
        Awaitility.await().ignoreExceptions().untilAsserted(() -> {
            Assertions.assertThat(client.apps().deployments()
                    .withName("apicurio-registry-operator-v" + projectVersion.toLowerCase()).get().getStatus()
                    .getReadyReplicas()).isEqualTo(1);
        });
    }
}
