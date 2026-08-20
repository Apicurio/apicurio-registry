package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.utils.RetryTest;
import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.Tag;

import static io.apicurio.registry.operator.Tags.OLM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@QuarkusTest
@Tag(OLM)
public class SmokeOLMITTest extends OLMITBase {

    @RetryTest
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
