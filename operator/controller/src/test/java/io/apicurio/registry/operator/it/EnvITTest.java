package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.resource.ResourceFactory;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static io.apicurio.registry.operator.api.v1.ContainerNames.*;
import static io.apicurio.registry.operator.resource.app.AppDeploymentResource.getContainerFromDeployment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@QuarkusTest
public class EnvITTest extends ITBase {

    private static final Logger log = LoggerFactory.getLogger(EnvITTest.class);

    private static final String[] defaultAppEnv = new String[]{
            "QUARKUS_PROFILE",
            "QUARKUS_HTTP_ACCESS_LOG_ENABLED",
            "QUARKUS_HTTP_CORS_ORIGINS"
    };

    private static final String[] defaultUIEnv = new String[] { "REGISTRY_API_URL" };

    @Test
    void testEnvVars() {
        var registry = ResourceFactory.deserialize("/k8s/examples/simple.apicurioregistry3.yaml",
                ApicurioRegistry3.class);
        registry.getMetadata().setNamespace(namespace);
        registry.withSpec().withApp().withIngress().setHost(ingressManager.getIngressHost("app"));
        registry.withSpec().withUi().withIngress().setHost(ingressManager.getIngressHost("ui"));

        client.resource(registry).create();

        await().ignoreExceptions().until(() -> {
                var appEnv = getContainerFromDeployment(client.apps().deployments().inNamespace(namespace).withName(registry.getMetadata().getName() + "-app-deployment").get(), REGISTRY_APP_CONTAINER_NAME).getEnv();
                assertThat(appEnv).map(EnvVar::getName).containsOnlyOnce(defaultAppEnv);

                var uiEnv = getContainerFromDeployment(client.apps().deployments().inNamespace(namespace).withName(registry.getMetadata().getName() + "-ui-deployment").get(), REGISTRY_UI_CONTAINER_NAME).getEnv();
                assertThat(uiEnv).map(EnvVar::getName).containsOnlyOnce(defaultUIEnv);

                return true;
        });

        registry.getSpec().getApp().setEnv(List.of(
                new EnvVarBuilder().withName("APP_VAR_1_NAME").withValue("APP_VAR_1_VALUE").build(),
                new EnvVarBuilder().withName("QUARKUS_HTTP_ACCESS_LOG_ENABLED").withValue("false").build(),
                new EnvVarBuilder().withName("APP_VAR_2_NAME").withValue("APP_VAR_2_VALUE").build()
        ));
        registry.getSpec().getUi().setEnv(List.of(
                new EnvVarBuilder().withName("UI_VAR_1_NAME").withValue("UI_VAR_1_VALUE").build(),
                new EnvVarBuilder().withName("REGISTRY_API_URL").withValue("FOO").build(),
                new EnvVarBuilder().withName("UI_VAR_2_NAME").withValue("UI_VAR_2_VALUE").build()
        ));

        await().ignoreExceptionsInstanceOf(KubernetesClientException.class).until(() -> {
            client.resource(registry).update();
            return true;
        });

        await().ignoreExceptions().until(() -> {

            var appEnv = getContainerFromDeployment(client.apps().deployments().inNamespace(namespace).withName(registry.getMetadata().getName() + "-app-deployment").get(), REGISTRY_APP_CONTAINER_NAME).getEnv();
            assertThat(appEnv).map(EnvVar::getName).containsOnlyOnce(defaultAppEnv);
            var QUARKUS_HTTP_ACCESS_LOG_ENABLED = appEnv.stream().filter(e -> "QUARKUS_HTTP_ACCESS_LOG_ENABLED".equals(e.getName())).map(EnvVar::getValue).findAny().get();
            assertThat(QUARKUS_HTTP_ACCESS_LOG_ENABLED).isEqualTo("false");
            assertThat(appEnv).containsSubsequence(
                    new EnvVarBuilder().withName("APP_VAR_1_NAME").withValue("APP_VAR_1_VALUE").build(),
                    new EnvVarBuilder().withName("APP_VAR_2_NAME").withValue("APP_VAR_2_VALUE").build()
            );

            var uiEnv = getContainerFromDeployment(client.apps().deployments().inNamespace(namespace).withName(registry.getMetadata().getName() + "-ui-deployment").get(), REGISTRY_UI_CONTAINER_NAME).getEnv();
            assertThat(uiEnv).map(EnvVar::getName).containsOnlyOnce(defaultUIEnv);
            var REGISTRY_API_URL = uiEnv.stream().filter(e -> "REGISTRY_API_URL".equals(e.getName())).map(EnvVar::getValue).findAny().get();
            assertThat(REGISTRY_API_URL).isEqualTo("FOO");
            assertThat(uiEnv).containsSubsequence(
                    new EnvVarBuilder().withName("UI_VAR_1_NAME").withValue("UI_VAR_1_VALUE").build(),
                    new EnvVarBuilder().withName("UI_VAR_2_NAME").withValue("UI_VAR_2_VALUE").build()
            );

            return true;
        });

        // Change order

        registry.getSpec().getApp().setEnv(List.of(
                new EnvVarBuilder().withName("APP_VAR_2_NAME").withValue("APP_VAR_2_VALUE").build(),
                new EnvVarBuilder().withName("QUARKUS_HTTP_ACCESS_LOG_ENABLED").withValue("false").build(),
                new EnvVarBuilder().withName("APP_VAR_1_NAME").withValue("APP_VAR_1_VALUE").build()
        ));
        registry.getSpec().getUi().setEnv(List.of(
                new EnvVarBuilder().withName("UI_VAR_2_NAME").withValue("UI_VAR_2_VALUE").build(),
                new EnvVarBuilder().withName("REGISTRY_API_URL").withValue("FOO").build(),
                new EnvVarBuilder().withName("UI_VAR_1_NAME").withValue("UI_VAR_1_VALUE").build()
        ));

        await().ignoreExceptionsInstanceOf(KubernetesClientException.class).until(() -> {
            client.resource(registry).update();
            return true;
        });

        await().ignoreExceptions().until(() -> {

            var appEnv = getContainerFromDeployment(client.apps().deployments().inNamespace(namespace).withName(registry.getMetadata().getName() + "-app-deployment").get(), REGISTRY_APP_CONTAINER_NAME).getEnv();
            assertThat(appEnv).map(EnvVar::getName).containsOnlyOnce(defaultAppEnv);
            var QUARKUS_HTTP_ACCESS_LOG_ENABLED = appEnv.stream().filter(e -> "QUARKUS_HTTP_ACCESS_LOG_ENABLED".equals(e.getName())).map(EnvVar::getValue).findAny().get();
            assertThat(QUARKUS_HTTP_ACCESS_LOG_ENABLED).isEqualTo("false");
            assertThat(appEnv).containsSubsequence(
                    new EnvVarBuilder().withName("APP_VAR_2_NAME").withValue("APP_VAR_2_VALUE").build(),
                    new EnvVarBuilder().withName("APP_VAR_1_NAME").withValue("APP_VAR_1_VALUE").build()
            );


            var uiEnv = getContainerFromDeployment(client.apps().deployments().inNamespace(namespace).withName(registry.getMetadata().getName() + "-ui-deployment").get(), REGISTRY_UI_CONTAINER_NAME).getEnv();
            assertThat(uiEnv).map(EnvVar::getName).containsOnlyOnce(defaultUIEnv);
            var REGISTRY_API_URL = uiEnv.stream().filter(e -> "REGISTRY_API_URL".equals(e.getName())).map(EnvVar::getValue).findAny().get();
            assertThat(REGISTRY_API_URL).isEqualTo("FOO");
            assertThat(uiEnv).containsSubsequence(
                    new EnvVarBuilder().withName("UI_VAR_2_NAME").withValue("UI_VAR_2_VALUE").build(),
                    new EnvVarBuilder().withName("UI_VAR_1_NAME").withValue("UI_VAR_1_VALUE").build()
            );

            return true;
        });
    }
}
