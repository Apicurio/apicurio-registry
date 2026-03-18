package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.resource.ResourceFactory;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.NetworkPolicy;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.apicurio.registry.operator.Tags.FEATURE_SETUP;
import static io.apicurio.registry.operator.api.v1.ContainerNames.REGISTRY_APP_CONTAINER_NAME;
import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_APP;
import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_UI;
import static io.apicurio.registry.operator.resource.app.AppDeploymentResource.getContainerFromDeployment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration tests for multitenancy Pattern B: Namespace per Tenant.
 * <p>
 * Pattern B deploys each tenant's ApicurioRegistry3 CR in a separate namespace,
 * while a single operator instance watches all namespaces. This provides stronger
 * isolation using Kubernetes RBAC, ResourceQuotas, and NetworkPolicies at the
 * namespace level.
 *
 * @see <a href="https://www.apicur.io/registry/docs/apicurio-registry/3.2.x/getting-started/assembly-implementing-multitenancy.html">Multitenancy Documentation</a>
 */
@QuarkusTest
@Tag(FEATURE_SETUP)
public class MultitenancyPatternBITTest extends ITBase {

    private static final Logger log = LoggerFactory.getLogger(MultitenancyPatternBITTest.class);

    private ApicurioRegistry3 createTenantRegistry(String tenantNamespace, String yamlPath) {
        var registry = ResourceFactory.deserialize(yamlPath, ApicurioRegistry3.class);
        registry.getMetadata().setNamespace(tenantNamespace);
        registry.getSpec().getApp().getIngress()
                .setHost(ingressManager.getIngressHost(tenantNamespace, "app"));
        registry.getSpec().getUi().getIngress()
                .setHost(ingressManager.getIngressHost(tenantNamespace, "ui"));
        client.resource(registry).create();
        return registry;
    }

    /**
     * Tests that multiple tenants deployed in separate namespaces each get their own
     * independent set of Kubernetes resources (Deployments, Services, Ingresses).
     */
    @Test
    void testNamespacePerTenantResourceIsolation() {

        var tenantAlphaNs = calculateNamespace();
        createNamespace(client, tenantAlphaNs);
        var tenantBetaNs = calculateNamespace();
        createNamespace(client, tenantBetaNs);
        var tenantGammaNs = calculateNamespace();
        createNamespace(client, tenantGammaNs);

        try {
            var registryAlpha = createTenantRegistry(tenantAlphaNs,
                    "/k8s/examples/multitenancy/tenant-alpha.apicurioregistry3.yaml");
            var registryBeta = createTenantRegistry(tenantBetaNs,
                    "/k8s/examples/multitenancy/tenant-beta.apicurioregistry3.yaml");
            var registryGamma = createTenantRegistry(tenantGammaNs,
                    "/k8s/examples/multitenancy/tenant-gamma.apicurioregistry3.yaml");

            // Verify each tenant gets independent Deployments
            checkDeploymentExists(registryAlpha, COMPONENT_APP, 1);
            checkDeploymentExists(registryAlpha, COMPONENT_UI, 1);

            // Tenant Beta has 2 app replicas
            checkDeploymentExists(registryBeta, COMPONENT_APP, 2);
            checkDeploymentExists(registryBeta, COMPONENT_UI, 1);

            checkDeploymentExists(registryGamma, COMPONENT_APP, 1);
            checkDeploymentExists(registryGamma, COMPONENT_UI, 1);

            // Verify each tenant gets independent Services
            checkServiceExists(registryAlpha, COMPONENT_APP);
            checkServiceExists(registryAlpha, COMPONENT_UI);

            checkServiceExists(registryBeta, COMPONENT_APP);
            checkServiceExists(registryBeta, COMPONENT_UI);

            checkServiceExists(registryGamma, COMPONENT_APP);
            checkServiceExists(registryGamma, COMPONENT_UI);

            // Verify each tenant gets independent Ingresses
            checkIngressExists(registryAlpha, COMPONENT_APP);
            checkIngressExists(registryAlpha, COMPONENT_UI);

            checkIngressExists(registryBeta, COMPONENT_APP);
            checkIngressExists(registryBeta, COMPONENT_UI);

            checkIngressExists(registryGamma, COMPONENT_APP);
            checkIngressExists(registryGamma, COMPONENT_UI);

            // Verify Ingress hosts are unique per tenant
            await().ignoreExceptions().untilAsserted(() -> {
                var alphaAppIngress = client.network().v1().ingresses()
                        .inNamespace(tenantAlphaNs)
                        .withName(registryAlpha.getMetadata().getName() + "-app-ingress").get();
                var betaAppIngress = client.network().v1().ingresses()
                        .inNamespace(tenantBetaNs)
                        .withName(registryBeta.getMetadata().getName() + "-app-ingress").get();
                var gammaAppIngress = client.network().v1().ingresses()
                        .inNamespace(tenantGammaNs)
                        .withName(registryGamma.getMetadata().getName() + "-app-ingress").get();

                var alphaHost = alphaAppIngress.getSpec().getRules().get(0).getHost();
                var betaHost = betaAppIngress.getSpec().getRules().get(0).getHost();
                var gammaHost = gammaAppIngress.getSpec().getRules().get(0).getHost();

                assertThat(alphaHost).isNotEqualTo(betaHost);
                assertThat(alphaHost).isNotEqualTo(gammaHost);
                assertThat(betaHost).isNotEqualTo(gammaHost);
            });

        } finally {
            cleanupTenantNamespaces(tenantAlphaNs, tenantBetaNs, tenantGammaNs);
        }
    }

    /**
     * Tests that each tenant in its own namespace gets independent NetworkPolicies,
     * providing network-level isolation between tenants.
     */
    @Test
    void testNetworkPolicyIsolationPerTenant() {

        var tenantAlphaNs = calculateNamespace();
        createNamespace(client, tenantAlphaNs);
        var tenantBetaNs = calculateNamespace();
        createNamespace(client, tenantBetaNs);

        try {
            var registryAlpha = createTenantRegistry(tenantAlphaNs,
                    "/k8s/examples/multitenancy/tenant-alpha.apicurioregistry3.yaml");
            var registryBeta = createTenantRegistry(tenantBetaNs,
                    "/k8s/examples/multitenancy/tenant-beta.apicurioregistry3.yaml");

            // Wait for deployments to be ready
            checkDeploymentExists(registryAlpha, COMPONENT_APP, 1);
            checkDeploymentExists(registryBeta, COMPONENT_APP, 2);

            // Verify each tenant gets its own NetworkPolicies
            NetworkPolicy alphaAppPolicy = checkNetworkPolicyExists(registryAlpha, COMPONENT_APP);
            NetworkPolicy alphaUiPolicy = checkNetworkPolicyExists(registryAlpha, COMPONENT_UI);
            NetworkPolicy betaAppPolicy = checkNetworkPolicyExists(registryBeta, COMPONENT_APP);
            NetworkPolicy betaUiPolicy = checkNetworkPolicyExists(registryBeta, COMPONENT_UI);

            // Verify NetworkPolicies are in their respective namespaces
            assertThat(alphaAppPolicy.getMetadata().getNamespace()).isEqualTo(tenantAlphaNs);
            assertThat(alphaUiPolicy.getMetadata().getNamespace()).isEqualTo(tenantAlphaNs);
            assertThat(betaAppPolicy.getMetadata().getNamespace()).isEqualTo(tenantBetaNs);
            assertThat(betaUiPolicy.getMetadata().getNamespace()).isEqualTo(tenantBetaNs);

            // Verify pod selectors target the correct instance in each namespace
            assertLabelsContains(alphaAppPolicy.getSpec().getPodSelector().getMatchLabels(),
                    "app.kubernetes.io/instance=" + registryAlpha.getMetadata().getName());
            assertLabelsContains(betaAppPolicy.getSpec().getPodSelector().getMatchLabels(),
                    "app.kubernetes.io/instance=" + registryBeta.getMetadata().getName());

        } finally {
            cleanupTenantNamespaces(tenantAlphaNs, tenantBetaNs);
        }
    }

    /**
     * Tests that tenants can be independently scaled without affecting other tenants.
     */
    @Test
    void testIndependentTenantScaling() {

        var tenantAlphaNs = calculateNamespace();
        createNamespace(client, tenantAlphaNs);
        var tenantBetaNs = calculateNamespace();
        createNamespace(client, tenantBetaNs);

        try {
            var registryAlpha = createTenantRegistry(tenantAlphaNs,
                    "/k8s/examples/multitenancy/tenant-alpha.apicurioregistry3.yaml");
            var registryBeta = createTenantRegistry(tenantBetaNs,
                    "/k8s/examples/multitenancy/tenant-beta.apicurioregistry3.yaml");

            // Verify initial replica counts
            checkDeploymentExists(registryAlpha, COMPONENT_APP, 1);
            checkDeploymentExists(registryBeta, COMPONENT_APP, 2);

            // Scale up tenant Alpha to 3 replicas
            registryAlpha.getSpec().getApp().setReplicas(3);
            client.resource(registryAlpha).update();

            // Verify Alpha scaled up while Beta remains unchanged
            checkDeploymentExists(registryAlpha, COMPONENT_APP, 3);
            checkDeploymentExists(registryBeta, COMPONENT_APP, 2);

            // Scale down tenant Beta to 1 replica
            registryBeta.getSpec().getApp().setReplicas(1);
            client.resource(registryBeta).update();

            // Verify Beta scaled down while Alpha remains unchanged
            checkDeploymentExists(registryAlpha, COMPONENT_APP, 3);
            checkDeploymentExists(registryBeta, COMPONENT_APP, 1);

        } finally {
            cleanupTenantNamespaces(tenantAlphaNs, tenantBetaNs);
        }
    }

    /**
     * Tests that deleting one tenant's CR does not affect other tenants' resources.
     */
    @Test
    void testTenantLifecycleIsolation() {

        var tenantAlphaNs = calculateNamespace();
        createNamespace(client, tenantAlphaNs);
        var tenantBetaNs = calculateNamespace();
        createNamespace(client, tenantBetaNs);
        var tenantGammaNs = calculateNamespace();
        createNamespace(client, tenantGammaNs);

        try {
            var registryAlpha = createTenantRegistry(tenantAlphaNs,
                    "/k8s/examples/multitenancy/tenant-alpha.apicurioregistry3.yaml");
            var registryBeta = createTenantRegistry(tenantBetaNs,
                    "/k8s/examples/multitenancy/tenant-beta.apicurioregistry3.yaml");
            var registryGamma = createTenantRegistry(tenantGammaNs,
                    "/k8s/examples/multitenancy/tenant-gamma.apicurioregistry3.yaml");

            // Wait for all tenants to be ready
            checkDeploymentExists(registryAlpha, COMPONENT_APP, 1);
            checkDeploymentExists(registryBeta, COMPONENT_APP, 2);
            checkDeploymentExists(registryGamma, COMPONENT_APP, 1);

            // Delete tenant Beta's CR
            log.info("Deleting tenant Beta CR in namespace {}", tenantBetaNs);
            client.resource(registryBeta).delete();

            // Wait for tenant Beta resources to be cleaned up
            await().atMost(MEDIUM_DURATION).ignoreExceptions().untilAsserted(() -> {
                assertThat(client.apps().deployments()
                        .inNamespace(tenantBetaNs)
                        .withName(registryBeta.getMetadata().getName() + "-app-deployment").get())
                        .isNull();
            });

            // Verify tenant Alpha and Gamma are unaffected
            checkDeploymentExists(registryAlpha, COMPONENT_APP, 1);
            checkServiceExists(registryAlpha, COMPONENT_APP);
            checkIngressExists(registryAlpha, COMPONENT_APP);

            checkDeploymentExists(registryGamma, COMPONENT_APP, 1);
            checkServiceExists(registryGamma, COMPONENT_APP);
            checkIngressExists(registryGamma, COMPONENT_APP);

        } finally {
            cleanupTenantNamespaces(tenantAlphaNs, tenantBetaNs, tenantGammaNs);
        }
    }

    /**
     * Tests that tenants in separate namespaces can have independent environment
     * variable configurations, simulating different per-tenant settings.
     */
    @Test
    void testIndependentTenantConfiguration() {

        var tenantAlphaNs = calculateNamespace();
        createNamespace(client, tenantAlphaNs);
        var tenantBetaNs = calculateNamespace();
        createNamespace(client, tenantBetaNs);

        try {
            // Create tenant Alpha with custom env vars
            var registryAlpha = ResourceFactory.deserialize(
                    "/k8s/examples/multitenancy/tenant-alpha.apicurioregistry3.yaml",
                    ApicurioRegistry3.class);
            registryAlpha.getMetadata().setNamespace(tenantAlphaNs);
            registryAlpha.getSpec().getApp().getIngress()
                    .setHost(ingressManager.getIngressHost(tenantAlphaNs, "app"));
            registryAlpha.getSpec().getUi().getIngress()
                    .setHost(ingressManager.getIngressHost(tenantAlphaNs, "ui"));
            registryAlpha.getSpec().getApp().setEnv(List.of(
                    new EnvVarBuilder().withName("TENANT_ID").withValue("alpha").build()
            ));
            client.resource(registryAlpha).create();

            // Create tenant Beta with different env vars
            var registryBeta = ResourceFactory.deserialize(
                    "/k8s/examples/multitenancy/tenant-beta.apicurioregistry3.yaml",
                    ApicurioRegistry3.class);
            registryBeta.getMetadata().setNamespace(tenantBetaNs);
            registryBeta.getSpec().getApp().getIngress()
                    .setHost(ingressManager.getIngressHost(tenantBetaNs, "app"));
            registryBeta.getSpec().getUi().getIngress()
                    .setHost(ingressManager.getIngressHost(tenantBetaNs, "ui"));
            registryBeta.getSpec().getApp().setEnv(List.of(
                    new EnvVarBuilder().withName("TENANT_ID").withValue("beta").build()
            ));
            client.resource(registryBeta).create();

            // Wait for deployments
            checkDeploymentExists(registryAlpha, COMPONENT_APP, 1);
            checkDeploymentExists(registryBeta, COMPONENT_APP, 2);

            // Verify each tenant has its own env var configuration
            await().ignoreExceptions().untilAsserted(() -> {
                var alphaEnv = getContainerFromDeployment(
                        client.apps().deployments().inNamespace(tenantAlphaNs)
                                .withName(registryAlpha.getMetadata().getName() + "-app-deployment").get(),
                        REGISTRY_APP_CONTAINER_NAME).getEnv();
                assertThat(alphaEnv).filteredOn(e -> "TENANT_ID".equals(e.getName()))
                        .hasSize(1)
                        .first()
                        .extracting(EnvVar::getValue)
                        .isEqualTo("alpha");

                var betaEnv = getContainerFromDeployment(
                        client.apps().deployments().inNamespace(tenantBetaNs)
                                .withName(registryBeta.getMetadata().getName() + "-app-deployment").get(),
                        REGISTRY_APP_CONTAINER_NAME).getEnv();
                assertThat(betaEnv).filteredOn(e -> "TENANT_ID".equals(e.getName()))
                        .hasSize(1)
                        .first()
                        .extracting(EnvVar::getValue)
                        .isEqualTo("beta");
            });

        } finally {
            cleanupTenantNamespaces(tenantAlphaNs, tenantBetaNs);
        }
    }

    private void cleanupTenantNamespaces(String... namespaces) {
        if (cleanup) {
            var nsList = List.of(namespaces);
            nsList.forEach(n -> {
                log.info("Deleting tenant namespace: {}", n);
                client.namespaces().withName(n).delete();
            });
            await().atMost(MEDIUM_DURATION).ignoreExceptions().untilAsserted(() -> {
                nsList.forEach(n -> assertThat(client.namespaces().withName(n).get()).isNull());
            });
        }
    }

    private void assertLabelsContains(Map<String, String> labels, String... values) {
        assertThat(labels.entrySet().stream()
                .map(l -> l.getKey() + "=" + l.getValue())
                .collect(Collectors.toSet())).contains(values);
    }
}
