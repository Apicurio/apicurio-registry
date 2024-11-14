package io.apicurio.registry.operator.it;

import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@QuarkusTest
@DisabledIf("io.apicurio.registry.operator.it.OLMITBase#disabledIf")
public class SmokeOLMITTest extends OLMITBase {

    @Test
    void smoke() {
        // Wait for the operator to deploy
        var projectVersion = ConfigProvider.getConfig().getValue(PROJECT_VERSION_PROP, String.class);
        await().ignoreExceptions().untilAsserted(() -> {
            assertThat(client.apps().deployments()
                    .withName("apicurio-registry-operator-v" + projectVersion.toLowerCase()).get().getStatus()
                    .getReadyReplicas()).isEqualTo(1);
        });
    }
}
