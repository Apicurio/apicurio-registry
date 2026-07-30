package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.resource.ResourceFactory;
import io.apicurio.registry.operator.utils.RetryTest;
import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.apicurio.registry.operator.Tags.OLM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Verifies the AllNamespaces install mode: with an OperatorGroup that has no targetNamespaces,
 * OLM creates cluster-wide RBAC (ClusterRole + ClusterRoleBinding) and the operator watches all
 * namespaces. The operand is deployed into a namespace other than the operator's own, which is
 * only possible with cluster-wide workload permissions.
 */
@QuarkusTest
@Tag(OLM)
public class AllNamespacesOLMITTest extends OLMITBase {

    private static final Logger log = LoggerFactory.getLogger(AllNamespacesOLMITTest.class);

    @Override
    protected String getOperatorGroupResourcePath() {
        return "olmv0/operator-group-all-namespaces.yaml";
    }

    @RetryTest
    void operandDeploysInAnotherNamespace() {
        // OperatorGroups (and the AllNamespaces install mode via empty targetNamespaces) are an
        // OLM v0 concept. In v1 mode setupOLMResources() ignores getOperatorGroupResourcePath(),
        // so this test only applies to OLM v0.
        assumeTrue(getOlmVersion() == 0, "AllNamespaces OperatorGroup test only applies to OLM v0");

        // Wait for the operator to be ready first.
        var projectVersion = ConfigProvider.getConfig().getValue(PROJECT_VERSION_PROP, String.class);
        await().ignoreExceptions().untilAsserted(() -> {
            assertThat(client.apps().deployments()
                    .withName("apicurio-registry-operator-v" + projectVersion.toLowerCase()).get().getStatus()
                    .getReadyReplicas()).isEqualTo(1);
        });

        // Create a second namespace that is NOT the operator's install namespace.
        var otherNamespace = ITBase.calculateNamespace();
        ITBase.createNamespace(client, otherNamespace);
        try {
            var registry = ResourceFactory.deserialize("/k8s/examples/simple.apicurioregistry3.yaml",
                    ApicurioRegistry3.class);
            registry.getMetadata().setNamespace(otherNamespace);
            registry.getSpec().getApp().getIngress().setHost(ingressManager.getIngressHost("app"));
            registry.getSpec().getUi().getIngress().setHost(ingressManager.getIngressHost("ui"));

            client.resource(registry).create();

            // In AllNamespaces mode the operator watches every namespace and has cluster-wide
            // workload permissions, so the operand Deployment is created and becomes ready.
            await().ignoreExceptions().untilAsserted(() -> {
                assertThat(client.apps().deployments().inNamespace(otherNamespace)
                        .withName(registry.getMetadata().getName() + "-app-deployment").get().getStatus()
                        .getReadyReplicas()).isEqualTo(1);
            });
        } finally {
            if (cleanup) {
                client.namespaces().withName(otherNamespace).delete();
            }
        }
    }
}
