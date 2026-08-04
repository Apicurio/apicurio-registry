package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.utils.RetryTest;
import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;

import java.io.IOException;

import static io.apicurio.registry.operator.Tags.OLM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@QuarkusTest
@Tag(OLM)
public class SmokeOLMITTest extends OLMITBase {

    private static final String WORKLOAD_CLUSTER_ROLE = "olmv1/operator-workload-clusterrole.yaml";
    private static final String WORKLOAD_CLUSTER_ROLE_BINDING = "olmv1/operator-workload-clusterrole-binding.yaml";

    /**
     * Models the admin step required for an all-namespace operator install under OLM v1. v1 has no
     * OperatorGroup to promote the CSV's namespace-scoped {@code permissions} to cluster scope, so an
     * operator watching all namespaces can't list workloads cluster-wide from the namespaced Role
     * alone and would CrashLoopBackOff. In a real v1 all-namespace install the admin grants the
     * operator ServiceAccount cluster-wide workload access; this creates that grant so the operator
     * becomes ready. OLM v0 needs nothing here, its OperatorGroup already scopes the RBAC.
     * <p>
     * Runs after {@link OLMITBase#beforeAll()} has started the install (superclass {@code @BeforeAll}
     * runs first), so {@code client}/{@code namespace} are set. The binding may reference the operator
     * SA before it exists, which is fine.
     */
    @BeforeAll
    void grantOperatorClusterWideWorkloadAccess() throws IOException {
        if (getOlmVersion() != 1) {
            return;
        }
        // Remove any leak from a previously crashed run first (these are cluster-scoped, fixed-name).
        OLMTestUtils.deleteResourceQuietly(client, namespace, WORKLOAD_CLUSTER_ROLE_BINDING);
        OLMTestUtils.deleteResourceQuietly(client, namespace, WORKLOAD_CLUSTER_ROLE);
        OLMTestUtils.createResource(client, namespace, WORKLOAD_CLUSTER_ROLE);
        OLMTestUtils.createResource(client, namespace, WORKLOAD_CLUSTER_ROLE_BINDING);
    }

    /**
     * Removes the cluster-scoped admin-step grant. Runs regardless of test outcome (subclass
     * {@code @AfterAll} runs before {@link OLMITBase#afterAll()}), so a smoke failure can't leak
     * cluster-wide workload access into the shared cluster for later OLM tests.
     */
    @AfterAll
    void revokeOperatorClusterWideWorkloadAccess() {
        if (getOlmVersion() != 1) {
            return;
        }
        OLMTestUtils.deleteResourceQuietly(client, namespace, WORKLOAD_CLUSTER_ROLE_BINDING);
        OLMTestUtils.deleteResourceQuietly(client, namespace, WORKLOAD_CLUSTER_ROLE);
    }

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
