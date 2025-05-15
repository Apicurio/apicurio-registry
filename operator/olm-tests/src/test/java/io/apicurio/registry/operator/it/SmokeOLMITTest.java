package io.apicurio.registry.operator.it;

import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.stream.Collectors;

import static io.apicurio.registry.operator.utils.Mapper.toYAML;

@QuarkusTest
public class SmokeOLMITTest extends OLMITBase {

    private static final Logger log = LoggerFactory.getLogger(SmokeOLMITTest.class);

    @Test
    void smoke() {
        // Wait for the operator to deploy
        var projectVersion = ConfigProvider.getConfig().getValue(PROJECT_VERSION_PROP, String.class);
        Awaitility.await().ignoreExceptions().untilAsserted(() -> {
            // debug
            log.warn(">>> \n\n{}\n\n", client.namespaces().list().getItems().stream().map(x -> x.getMetadata().getName()).toList());

            log.warn(">>> \n\n{}\n\n", client.pods().list().getItems().stream().map(x -> ResourceID.fromResource(x)).toList());
            log.warn(">>> \n\n{}\n\n", client.pods().list().getItems().stream().map(x -> toYAML(x)).collect(Collectors.joining("\n\n--------------\n\n")));

            log.warn(">>> \n\n{}\n\n", client.apps().deployments().list().getItems().stream().map(x -> ResourceID.fromResource(x)).toList());
            log.warn(">>> \n\n{}\n\n", client.apps().deployments().list().getItems().stream().map(x -> toYAML(x)).collect(Collectors.joining("\n\n--------------\n\n")));

            Assertions.assertThat(client.apps().deployments()
                    .withName("apicurio-registry-operator-v" + projectVersion.toLowerCase()).get().getStatus()
                    .getReadyReplicas()).isEqualTo(1);
        });
    }
}
