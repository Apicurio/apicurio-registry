package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.resource.ResourceFactory;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static io.apicurio.registry.operator.resource.ResourceFactory.APP_CONTAINER_NAME;
import static io.apicurio.registry.operator.resource.ResourceFactory.UI_CONTAINER_NAME;
import static io.apicurio.registry.operator.resource.app.AppDeploymentResource.getContainerFromDeployment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@QuarkusTest
public class EnvITTest extends ITBase {

    private static final Logger log = LoggerFactory.getLogger(EnvITTest.class);

    // spotless:off
    private static final String[] defaultAppEnv = new String[]{
            "QUARKUS_PROFILE",
            "APICURIO_CONFIG_CACHE_ENABLED",
            "QUARKUS_HTTP_ACCESS_LOG_ENABLED",
            "QUARKUS_HTTP_CORS_ORIGINS",
            "APICURIO_REST_DELETION_GROUP_ENABLED",
            "APICURIO_REST_DELETION_ARTIFACT_ENABLED",
            "APICURIO_REST_DELETION_ARTIFACTVERSION_ENABLED",
            "APICURIO_APIS_V2_DATE_FORMAT"
    };
    // spotless:on

    private static final String[] defaultUIEnv = new String[] { "REGISTRY_API_URL" };

    @Test
    void testEnvVars() {
        // spotless:off
        var registry = ResourceFactory.deserialize("/k8s/examples/simple.apicurioregistry3.yaml",
                ApicurioRegistry3.class);
        registry.getMetadata().setNamespace(namespace);
        registry.getSpec().getApp().setHost(ingressManager.getIngressHost("app"));
        registry.getSpec().getUi().setHost(ingressManager.getIngressHost("ui"));

        client.resource(registry).create();

        await().ignoreExceptions().until(() -> {

            var appEnv = getContainerFromDeployment(client.apps().deployments().inNamespace(namespace).withName(registry.getMetadata().getName() + "-app-deployment").get(), APP_CONTAINER_NAME).getEnv();
            assertThat(appEnv).map(EnvVar::getName).containsOnlyOnce(defaultAppEnv);
            var QUARKUS_HTTP_ACCESS_LOG_ENABLED = appEnv.stream().filter(e -> "QUARKUS_HTTP_ACCESS_LOG_ENABLED".equals(e.getName())).map(EnvVar::getValue).findAny().get();
            assertThat(QUARKUS_HTTP_ACCESS_LOG_ENABLED).isEqualTo("true");

            var uiEnv = getContainerFromDeployment(client.apps().deployments().inNamespace(namespace).withName(registry.getMetadata().getName() + "-ui-deployment").get(), UI_CONTAINER_NAME).getEnv();
            assertThat(uiEnv).map(EnvVar::getName).containsOnlyOnce(defaultUIEnv);
            var REGISTRY_API_URL = uiEnv.stream().filter(e -> "REGISTRY_API_URL".equals(e.getName())).map(EnvVar::getValue).findAny().get();
            assertThat(REGISTRY_API_URL).startsWith("http://");

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

        client.resource(registry).update();

        await().ignoreExceptions().until(() -> {

            var appEnv = getContainerFromDeployment(client.apps().deployments().inNamespace(namespace).withName(registry.getMetadata().getName() + "-app-deployment").get(), APP_CONTAINER_NAME).getEnv();
            assertThat(appEnv).map(EnvVar::getName).containsOnlyOnce(defaultAppEnv);
            var QUARKUS_HTTP_ACCESS_LOG_ENABLED = appEnv.stream().filter(e -> "QUARKUS_HTTP_ACCESS_LOG_ENABLED".equals(e.getName())).map(EnvVar::getValue).findAny().get();
            assertThat(QUARKUS_HTTP_ACCESS_LOG_ENABLED).isEqualTo("false");
            assertThat(appEnv).containsSubsequence(
                    new EnvVarBuilder().withName("APP_VAR_1_NAME").withValue("APP_VAR_1_VALUE").build(),
                    new EnvVarBuilder().withName("APP_VAR_2_NAME").withValue("APP_VAR_2_VALUE").build()
            );

            var uiEnv = getContainerFromDeployment(client.apps().deployments().inNamespace(namespace).withName(registry.getMetadata().getName() + "-ui-deployment").get(), UI_CONTAINER_NAME).getEnv();
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

        client.resource(registry).update();

        await().ignoreExceptions().until(() -> {

            var appEnv = getContainerFromDeployment(client.apps().deployments().inNamespace(namespace).withName(registry.getMetadata().getName() + "-app-deployment").get(), APP_CONTAINER_NAME).getEnv();
            assertThat(appEnv).map(EnvVar::getName).containsOnlyOnce(defaultAppEnv);
            var QUARKUS_HTTP_ACCESS_LOG_ENABLED = appEnv.stream().filter(e -> "QUARKUS_HTTP_ACCESS_LOG_ENABLED".equals(e.getName())).map(EnvVar::getValue).findAny().get();
            assertThat(QUARKUS_HTTP_ACCESS_LOG_ENABLED).isEqualTo("false");
            assertThat(appEnv).containsSubsequence(
                    new EnvVarBuilder().withName("APP_VAR_2_NAME").withValue("APP_VAR_2_VALUE").build(),
                    new EnvVarBuilder().withName("APP_VAR_1_NAME").withValue("APP_VAR_1_VALUE").build()
            );


            var uiEnv = getContainerFromDeployment(client.apps().deployments().inNamespace(namespace).withName(registry.getMetadata().getName() + "-ui-deployment").get(), UI_CONTAINER_NAME).getEnv();
            assertThat(uiEnv).map(EnvVar::getName).containsOnlyOnce(defaultUIEnv);
            var REGISTRY_API_URL = uiEnv.stream().filter(e -> "REGISTRY_API_URL".equals(e.getName())).map(EnvVar::getValue).findAny().get();
            assertThat(REGISTRY_API_URL).isEqualTo("FOO");
            assertThat(uiEnv).containsSubsequence(
                    new EnvVarBuilder().withName("UI_VAR_2_NAME").withValue("UI_VAR_2_VALUE").build(),
                    new EnvVarBuilder().withName("UI_VAR_1_NAME").withValue("UI_VAR_1_VALUE").build()
            );

            return true;
        });
        // spotless:on
    }
}
