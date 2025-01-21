package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.resource.ResourceFactory;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

import static io.apicurio.registry.operator.it.SmokeITTest.ingressDisabled;
import static io.apicurio.registry.operator.resource.ResourceFactory.*;
import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;

@QuarkusTest
public class StudioSmokeITTest extends ITBase {

    private static final Logger log = LoggerFactory.getLogger(StudioSmokeITTest.class);

    /**
     * Scenario: We want to check that the Studio component is not deployed by default unless the enabled
     * field is set to true, while checking that the basic Kubernetes resources are deployed as expected. We
     * do not check Registry components in detail, because that's done in other tests.
     */
    @Test
    void smoke() {

        var simpleRegistry = ResourceFactory.deserialize("/k8s/examples/simple.apicurioregistry3.yaml",
                ApicurioRegistry3.class);
        simpleRegistry.getMetadata().setNamespace(namespace);
        simpleRegistry.getSpec().getApp().getIngress().setHost(ingressManager.getIngressHost(COMPONENT_APP));
        simpleRegistry.getSpec().getUi().getIngress().setHost(ingressManager.getIngressHost(COMPONENT_UI));

        client.resource(simpleRegistry).create();

        checkDeploymentExists(simpleRegistry, COMPONENT_APP, 1);
        checkDeploymentExists(simpleRegistry, COMPONENT_UI, 1);
        checkDeploymentDoesNotExist(simpleRegistry, COMPONENT_STUDIO_UI);

        checkServiceExists(simpleRegistry, COMPONENT_APP);
        checkServiceExists(simpleRegistry, COMPONENT_UI);
        checkServiceDoesNotExist(simpleRegistry, COMPONENT_STUDIO_UI);

        checkIngressExists(simpleRegistry, COMPONENT_APP);
        checkIngressExists(simpleRegistry, COMPONENT_UI);
        checkIngressDoesNotExist(simpleRegistry, COMPONENT_STUDIO_UI);

        // Now let's enable the component, but without the host, which should not create an Ingress
        simpleRegistry.getSpec().withStudioUi().setEnabled(true);
        client.resource(simpleRegistry).update();

        checkDeploymentExists(simpleRegistry, COMPONENT_APP, 1);
        checkDeploymentExists(simpleRegistry, COMPONENT_UI, 1);
        checkDeploymentExists(simpleRegistry, COMPONENT_STUDIO_UI, 1);

        checkServiceExists(simpleRegistry, COMPONENT_APP);
        checkServiceExists(simpleRegistry, COMPONENT_UI);
        checkServiceExists(simpleRegistry, COMPONENT_STUDIO_UI);

        checkIngressExists(simpleRegistry, COMPONENT_APP);
        checkIngressExists(simpleRegistry, COMPONENT_UI);
        checkIngressDoesNotExist(simpleRegistry, COMPONENT_STUDIO_UI);

        // Now add the host
        simpleRegistry.getSpec().withStudioUi().withIngress()
                .setHost(ingressManager.getIngressHost(COMPONENT_STUDIO_UI));
        client.resource(simpleRegistry).update();

        checkIngressExists(simpleRegistry, COMPONENT_APP);
        checkIngressExists(simpleRegistry, COMPONENT_UI);
        checkIngressExists(simpleRegistry, COMPONENT_STUDIO_UI);

        // Check Service with port-forwarding
        int studioUiServicePort = portForwardManager
                .startPortForward(simpleRegistry.getMetadata().getName() + "-studio-ui-service", 8080);

        await().ignoreExceptions().until(() -> {
            given().get(new URI("http://localhost:" + studioUiServicePort + "/config.js")).then()
                    .statusCode(200);
            return true;
        });

        // Check Ingress, if enabled
        if (!ingressDisabled()) {
            await().ignoreExceptions().until(() -> {
                ingressManager.startHttpRequest(simpleRegistry.getMetadata().getName() + "-studio-ui-ingress")
                        .basePath("/config.js").get().then().statusCode(200);
                return true;
            });
        }

        // Now disable the component again, first only the Ingress
        simpleRegistry.getSpec().getStudioUi().getIngress().setHost(null);
        client.resource(simpleRegistry).update();

        checkDeploymentExists(simpleRegistry, COMPONENT_APP, 1);
        checkDeploymentExists(simpleRegistry, COMPONENT_UI, 1);
        checkDeploymentExists(simpleRegistry, COMPONENT_STUDIO_UI, 1);

        checkServiceExists(simpleRegistry, COMPONENT_APP);
        checkServiceExists(simpleRegistry, COMPONENT_UI);
        checkServiceExists(simpleRegistry, COMPONENT_STUDIO_UI);

        checkIngressExists(simpleRegistry, COMPONENT_APP);
        checkIngressExists(simpleRegistry, COMPONENT_UI);
        checkIngressDoesNotExist(simpleRegistry, COMPONENT_STUDIO_UI);

        // Now disable the entire Studio component
        simpleRegistry.getSpec().getStudioUi().setEnabled(false);
        client.resource(simpleRegistry).update();

        checkDeploymentExists(simpleRegistry, COMPONENT_APP, 1);
        checkDeploymentExists(simpleRegistry, COMPONENT_UI, 1);
        checkDeploymentDoesNotExist(simpleRegistry, COMPONENT_STUDIO_UI);

        checkServiceExists(simpleRegistry, COMPONENT_APP);
        checkServiceExists(simpleRegistry, COMPONENT_UI);
        checkServiceDoesNotExist(simpleRegistry, COMPONENT_STUDIO_UI);

        checkIngressExists(simpleRegistry, COMPONENT_APP);
        checkIngressExists(simpleRegistry, COMPONENT_UI);
        checkIngressDoesNotExist(simpleRegistry, COMPONENT_STUDIO_UI);
    }

    /**
     * Scenario: We want to check that the Studio component is not deployed by default unless the enabled
     * field is set to true, and, when activated, the number of replicas is set to the value specified while
     * checking that the basic Kubernetes resources are deployed as expected. We do not check Registry
     * components in detail, because that's done in other tests.
     */
    @Test
    void replicas() {
        var simpleRegistry = ResourceFactory.deserialize("/k8s/examples/simple.apicurioregistry3.yaml",
                ApicurioRegistry3.class);

        simpleRegistry.getMetadata().setNamespace(namespace);
        simpleRegistry.getSpec().getApp().setHost(ingressManager.getIngressHost(COMPONENT_APP));
        simpleRegistry.getSpec().getUi().setHost(ingressManager.getIngressHost(COMPONENT_UI));

        client.resource(simpleRegistry).create();

        // We start with one replica for Registry
        checkDeploymentExists(simpleRegistry, COMPONENT_APP, 1);
        checkDeploymentExists(simpleRegistry, COMPONENT_UI, 1);
        checkDeploymentDoesNotExist(simpleRegistry, COMPONENT_STUDIO_UI);

        // Now let's enable the Studio component and scale Registry to 3 replicas
        simpleRegistry.getSpec().withStudioUi().setEnabled(true);
        simpleRegistry.getSpec().getApp().setReplicas(3);
        simpleRegistry.getSpec().getUi().setReplicas(3);
        simpleRegistry.getSpec().getStudioUi().setReplicas(3);
        client.resource(simpleRegistry).update();

        checkDeploymentExists(simpleRegistry, COMPONENT_APP, 3);
        checkDeploymentExists(simpleRegistry, COMPONENT_UI, 3);
        checkDeploymentExists(simpleRegistry, COMPONENT_STUDIO_UI, 3);
    }
}
