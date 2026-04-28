package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.resource.ResourceFactory;
import io.apicurio.registry.utils.Cell;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.coordination.v1.Lease;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.apicurio.registry.operator.Tags.FEATURE;
import static io.apicurio.registry.operator.Tags.FEATURE_SETUP;
import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_APP;
import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_UI;
import static io.apicurio.registry.operator.utils.K8sCell.k8sCell;
import static io.apicurio.registry.utils.Cell.cell;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@QuarkusTest
@Tag(FEATURE)
@Tag(FEATURE_SETUP)
public class LeaderElectionITTest extends ITBase {

    private static final Logger log = LoggerFactory.getLogger(LeaderElectionITTest.class);

    private static final String LEADER_ELECTION_ENABLED_ENV = "APICURIO_OPERATOR_LEADER_ELECTION_ENABLED";
    private static final String LEADER_ELECTION_LEASE_NAME_ENV = "APICURIO_OPERATOR_LEADER_ELECTION_LEASE_NAME";
    private static final String CUSTOM_LEASE_NAME = "custom-leader-lease";
    private static final String DEFAULT_LEASE_NAME = "apicurio-registry-operator-lease";

    @Test
    void testLeaderElectionCreatesLease() {
        if (operatorDeployment == OperatorDeployment.local) {
            log.warn("This test requires an ability to edit the operator Deployment, so it's not supported when running locally.");
            return;
        }

        var operatorDeployment = k8sCell(client, ITBase::getOperatorDeployment);
        Cell<EnvVar> originalEnabledEnvVar = cell();

        try {
            // Enable leader election on the operator Deployment
            operatorDeployment.update(r -> {
                var container = r.getSpec().getTemplate().getSpec().getContainers().stream()
                        .filter(c -> "apicurio-registry-operator".equals(c.getName()))
                        .findFirst().orElseThrow();

                // Save the original env var if it exists, or note that it was absent
                container.getEnv().stream()
                        .filter(e -> LEADER_ELECTION_ENABLED_ENV.equals(e.getName()))
                        .findFirst()
                        .ifPresent(e -> originalEnabledEnvVar.set(new EnvVar(e.getName(), e.getValue(), e.getValueFrom())));

                // Set leader election enabled
                container.getEnv().removeIf(e -> LEADER_ELECTION_ENABLED_ENV.equals(e.getName()));
                container.getEnv().add(new EnvVar(LEADER_ELECTION_ENABLED_ENV, "true", null));
            });

            // Wait for the operator pod to restart with the new env var
            await().atMost(LONG_DURATION).ignoreExceptions().untilAsserted(() -> {
                var pod = waitOnOperatorPodReady();
                var envValue = getEnvVarValue(pod.getSpec(), LEADER_ELECTION_ENABLED_ENV);
                assertThat(envValue).isEqualTo("true");
            });
            startOperatorPodLog();

            // Verify the Lease resource was created in the operator namespace
            await().atMost(MEDIUM_DURATION).ignoreExceptions().untilAsserted(() -> {
                Lease lease = client.leases().inNamespace(namespace)
                        .withName(DEFAULT_LEASE_NAME).get();
                assertThat(lease).isNotNull();
                assertThat(lease.getSpec().getHolderIdentity()).isNotBlank();
                log.info("Leader election lease found: holder={}", lease.getSpec().getHolderIdentity());
            });

            // Verify the operator is still functional by deploying a registry
            var registry = ResourceFactory.deserialize("/k8s/examples/simple.apicurioregistry3.yaml",
                    ApicurioRegistry3.class);
            registry.getMetadata().setNamespace(namespace);
            client.resource(registry).create();

            checkDeploymentExists(registry, COMPONENT_APP, 1);
            checkDeploymentExists(registry, COMPONENT_UI, 1);

        } finally {
            // Restore the original env var
            operatorDeployment.update(r -> {
                var container = r.getSpec().getTemplate().getSpec().getContainers().stream()
                        .filter(c -> "apicurio-registry-operator".equals(c.getName()))
                        .findFirst().orElseThrow();

                container.getEnv().removeIf(e -> LEADER_ELECTION_ENABLED_ENV.equals(e.getName()));
                if (originalEnabledEnvVar.isSet()) {
                    container.getEnv().add(originalEnabledEnvVar.get());
                }
            });

            // Clean up the lease resource
            client.leases().inNamespace(namespace).withName(DEFAULT_LEASE_NAME).delete();

            // Wait for restoration to complete
            await().atMost(LONG_DURATION).ignoreExceptions().untilAsserted(() -> {
                waitOnOperatorPodReady();
            });
            startOperatorPodLog();
        }
    }

    @Test
    void testLeaderElectionCustomLeaseName() {
        if (operatorDeployment == OperatorDeployment.local) {
            log.warn("This test requires an ability to edit the operator Deployment, so it's not supported when running locally.");
            return;
        }

        var operatorDeployment = k8sCell(client, ITBase::getOperatorDeployment);
        Cell<EnvVar> originalEnabledEnvVar = cell();
        Cell<EnvVar> originalLeaseNameEnvVar = cell();

        // Delete any pre-existing default lease from a previous test
        client.leases().inNamespace(namespace).withName(DEFAULT_LEASE_NAME).delete();

        try {
            // Enable leader election with a custom lease name
            operatorDeployment.update(r -> {
                var container = r.getSpec().getTemplate().getSpec().getContainers().stream()
                        .filter(c -> "apicurio-registry-operator".equals(c.getName()))
                        .findFirst().orElseThrow();

                // Save originals
                container.getEnv().stream()
                        .filter(e -> LEADER_ELECTION_ENABLED_ENV.equals(e.getName()))
                        .findFirst()
                        .ifPresent(e -> originalEnabledEnvVar.set(new EnvVar(e.getName(), e.getValue(), e.getValueFrom())));
                container.getEnv().stream()
                        .filter(e -> LEADER_ELECTION_LEASE_NAME_ENV.equals(e.getName()))
                        .findFirst()
                        .ifPresent(e -> originalLeaseNameEnvVar.set(new EnvVar(e.getName(), e.getValue(), e.getValueFrom())));

                // Set leader election enabled with custom lease name
                container.getEnv().removeIf(e -> LEADER_ELECTION_ENABLED_ENV.equals(e.getName()));
                container.getEnv().removeIf(e -> LEADER_ELECTION_LEASE_NAME_ENV.equals(e.getName()));
                container.getEnv().add(new EnvVar(LEADER_ELECTION_ENABLED_ENV, "true", null));
                container.getEnv().add(new EnvVar(LEADER_ELECTION_LEASE_NAME_ENV, CUSTOM_LEASE_NAME, null));
            });

            // Wait for the operator pod to restart
            await().atMost(LONG_DURATION).ignoreExceptions().untilAsserted(() -> {
                var pod = waitOnOperatorPodReady();
                var envValue = getEnvVarValue(pod.getSpec(), LEADER_ELECTION_LEASE_NAME_ENV);
                assertThat(envValue).isEqualTo(CUSTOM_LEASE_NAME);
            });
            startOperatorPodLog();

            // Verify the Lease resource was created with the custom name
            await().atMost(MEDIUM_DURATION).ignoreExceptions().untilAsserted(() -> {
                Lease lease = client.leases().inNamespace(namespace)
                        .withName(CUSTOM_LEASE_NAME).get();
                assertThat(lease).isNotNull();
                assertThat(lease.getSpec().getHolderIdentity()).isNotBlank();
                log.info("Custom leader election lease found: name={}, holder={}",
                        CUSTOM_LEASE_NAME, lease.getSpec().getHolderIdentity());
            });

            // Verify the default lease name was NOT used
            Lease defaultLease = client.leases().inNamespace(namespace)
                    .withName(DEFAULT_LEASE_NAME).get();
            assertThat(defaultLease).isNull();

        } finally {
            // Restore original env vars
            operatorDeployment.update(r -> {
                var container = r.getSpec().getTemplate().getSpec().getContainers().stream()
                        .filter(c -> "apicurio-registry-operator".equals(c.getName()))
                        .findFirst().orElseThrow();

                container.getEnv().removeIf(e -> LEADER_ELECTION_ENABLED_ENV.equals(e.getName()));
                container.getEnv().removeIf(e -> LEADER_ELECTION_LEASE_NAME_ENV.equals(e.getName()));
                if (originalEnabledEnvVar.isSet()) {
                    container.getEnv().add(originalEnabledEnvVar.get());
                }
                if (originalLeaseNameEnvVar.isSet()) {
                    container.getEnv().add(originalLeaseNameEnvVar.get());
                }
            });

            // Clean up lease resources
            client.leases().inNamespace(namespace).withName(CUSTOM_LEASE_NAME).delete();
            client.leases().inNamespace(namespace).withName(DEFAULT_LEASE_NAME).delete();

            await().atMost(LONG_DURATION).ignoreExceptions().untilAsserted(() -> {
                waitOnOperatorPodReady();
            });
            startOperatorPodLog();
        }
    }

    private static String getEnvVarValue(PodSpec ps, String envVarName) {
        return ps.getContainers().stream()
                .filter(c -> "apicurio-registry-operator".equals(c.getName()))
                .flatMap(c -> c.getEnv().stream())
                .filter(e -> envVarName.equals(e.getName()))
                .map(EnvVar::getValue)
                .findFirst()
                .orElse(null);
    }
}
