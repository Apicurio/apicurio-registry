package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3Spec;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@QuarkusTest
public class SmokeITTest extends ITBase {

    private static final Logger log = LoggerFactory.getLogger(SmokeITTest.class);

    @Test
    void demoDeployment() {
        // spotless:off
        var registry = new ApicurioRegistry3();
        var meta = new ObjectMeta();
        meta.setName("demo");
        meta.setNamespace(getNamespace());
        registry.setMetadata(meta);
        registry.setSpec(ApicurioRegistry3Spec.builder()
                .appHost("app-todo")
                .uiHost("ui-todo")
                .build());
        // spotless:on

        // Act
        client.resources(ApicurioRegistry3.class).inNamespace(getNamespace()).create(registry);

        // Deployments
        await().ignoreExceptions().until(() -> {
            assertThat(client.apps().deployments().inNamespace(getNamespace()).withName("demo-app-deployment")
                    .get().getStatus().getReadyReplicas()).isEqualTo(1);
            assertThat(client.apps().deployments().inNamespace(getNamespace()).withName("demo-ui-deployment")
                    .get().getStatus().getReadyReplicas()).isEqualTo(1);
            return true;
        });

        // Services
        await().ignoreExceptions().until(() -> {
            assertThat(client.services().inNamespace(getNamespace()).withName("demo-app-service").get()
                    .getSpec().getClusterIP()).isNotBlank();
            assertThat(client.services().inNamespace(getNamespace()).withName("demo-ui-service").get()
                    .getSpec().getClusterIP()).isNotBlank();
            return true;
        });

        // Ingresses
        await().ignoreExceptions().until(() -> {
            assertThat(client.network().v1().ingresses().inNamespace(getNamespace())
                    .withName("demo-app-ingress").get().getSpec().getRules().get(0).getHost())
                    .isEqualTo("app-todo");
            assertThat(client.network().v1().ingresses().inNamespace(getNamespace())
                    .withName("demo-ui-ingress").get().getSpec().getRules().get(0).getHost())
                    .isEqualTo("ui-todo");
            return true;
        });
    }
}
