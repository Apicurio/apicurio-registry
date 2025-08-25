package io.apicurio.registry.operator.it;

import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static io.apicurio.registry.operator.utils.Mapper.toYAML;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@QuarkusTest
@Tag("OLM")
public class SmokeOLMITTest extends OLMITBase {

    private static final Logger log = LoggerFactory.getLogger(SmokeOLMITTest.class);

    @Test
    void smoke() {
        try {
            // Wait for the operator to deploy
            var projectVersion = ConfigProvider.getConfig().getValue(PROJECT_VERSION_PROP, String.class);
            await().ignoreExceptions().untilAsserted(() -> {
                assertThat(client.apps().deployments()
                        .withName("apicurio-registry-operator-v" + projectVersion.toLowerCase()).get().getStatus()
                        .getReadyReplicas()).isEqualTo(1);
            });
        } finally {
            client.apps().deployments().inNamespace(namespace).list().getItems().forEach(r -> {
                log.warn(">>>> deployment >>>> {}\n{}", ResourceID.fromResource(r), toYAML(r.getStatus()));
            });
            client.pods().inNamespace(namespace).list().getItems().forEach(r -> {
                log.warn(">>>> pod >>>> {}\n{}", ResourceID.fromResource(r), toYAML(r.getStatus()));
            });
            List.of(
                    Pair.of("olm.operatorframework.io/v1", "ClusterCatalog"),
                    Pair.of("olm.operatorframework.io/v1", "ClusterExtension")
            ).forEach(x -> {
                client.genericKubernetesResources(x.getLeft(), x.getRight()).list().getItems().forEach(r -> {
                    log.warn(">>>> {} >>>> {}\n{}", x.getRight(), ResourceID.fromResource(r), toYAML(r.get("status")));
                });
            });
        }
    }
}
