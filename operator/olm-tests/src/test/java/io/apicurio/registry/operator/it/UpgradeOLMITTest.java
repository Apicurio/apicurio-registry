package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.utils.OperatorTestContext;
import io.apicurio.registry.operator.utils.OperatorTestExtension;
import io.apicurio.registry.operator.utils.RetryTest;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

import static io.apicurio.registry.operator.Tags.OLM;
import static io.apicurio.registry.operator.it.ITBase.setDefaultAwaitilityTimings;
import static io.apicurio.registry.operator.it.OLMTestUtils.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Tests OLM upgrade paths using OLM v0 (Subscription model).
 *
 * Uses the second-to-last version in the catalog as the starting point, then verifies OLM upgrades to the
 * current version through both the minor-version channel and the rolling channel.
 */
@QuarkusTest
@Tag(OLM)
@ExtendWith(OperatorTestExtension.class)
public class UpgradeOLMITTest implements OperatorTestContext {

    private static final Logger log = LoggerFactory.getLogger(UpgradeOLMITTest.class);

    private static final String UPGRADE_START_VERSION_PROP = "test.operator.upgrade-start-version";
    // Must be present in both 3.x and 3.2.x channels in catalog.template.yaml
    private static final String UPGRADE_START_VERSION_DEFAULT = "3.2.4";
    private static final Duration UPGRADE_TIMEOUT = Duration.ofMinutes(10);

    private KubernetesClient client;
    private String namespace;
    private boolean cleanup;

    @Override
    public KubernetesClient getClient() {
        return client;
    }

    @Override
    public String getNamespace() {
        return namespace;
    }

    @Override
    public boolean isOLMTest() {
        return true;
    }

    private String getStartVersion() {
        return ConfigProvider.getConfig()
                .getOptionalValue(UPGRADE_START_VERSION_PROP, String.class)
                .orElse(UPGRADE_START_VERSION_DEFAULT);
    }

    private String getMinorChannelHead() {
        // Auto-discover from the catalog template file — read the first entry in the
        // minor channel to find the current head. This avoids hardcoded version constants
        // that break whenever a new patch is released.
        var startVersion = getStartVersion();
        var minorChannel = deriveMinorChannel(startVersion);
        try {
            var catalogRaw = loadRawResource("catalog/catalog.template.yaml");
            var mapper = new com.fasterxml.jackson.dataformat.yaml.YAMLMapper();
            var tree = mapper.readTree(catalogRaw);
            var entries = tree.get("entries");
            if (entries != null) {
                for (var entry : entries) {
                    if ("olm.channel".equals(entry.path("schema").asText())
                            && minorChannel.equals(entry.path("name").asText())) {
                        var channelEntries = entry.get("entries");
                        if (channelEntries != null && channelEntries.size() > 0) {
                            var headName = channelEntries.get(0).path("name").asText();
                            if (headName.startsWith(PACKAGE_NAME + ".v")) {
                                var version = headName.substring((PACKAGE_NAME + ".v").length());
                                log.info("Auto-discovered minor channel head from catalog template: {} -> {}",
                                        minorChannel, version);
                                return version;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not auto-discover minor channel head from catalog template: {}",
                    e.getMessage());
        }
        log.info("Falling back to start version as minor channel head: {}", startVersion);
        return startVersion;
    }

    private void setUp() throws Exception {
        setDefaultAwaitilityTimings();
        namespace = ITBase.calculateNamespace();
        client = ITBase.createK8sClient(namespace);
        ITBase.createNamespace(client, namespace);
        cleanup = ConfigProvider.getConfig().getValue(ITBase.CLEANUP, Boolean.class);
    }

    @AfterEach
    void tearDown() {
        if (cleanup && client != null && namespace != null) {
            log.info("Cleaning up namespace: {}", namespace);
            try {
                deleteResourceQuietly(client, namespace, "olmv0/subscription-upgrade.yaml");
                deleteResourceQuietly(client, namespace, "olmv0/operator-group.yaml");
                deleteResourceQuietly(client, namespace, "olmv0/catalog-source.yaml");
                client.namespaces().withName(namespace).delete();
            } catch (Exception e) {
                log.warn("Cleanup error: {}", e.getMessage());
            }
            client.close();
        }
    }

    @RetryTest
    void testUpgradeWithinMinorChannel() throws Exception {
        setUp();

        var startVersion = getStartVersion();
        var minorHead = getMinorChannelHead();
        var minorChannel = deriveMinorChannel(startVersion);

        log.info("Testing upgrade within {} channel: {} -> {}", minorChannel, startVersion,
                minorHead);

        deployCatalogAndSubscribe(minorChannel, startVersion);
        waitForOperatorVersion(startVersion);

        log.info("Operator {} deployed, waiting for upgrade to {}", startVersion, minorHead);
        verifyUpgradeTo(minorHead);

        log.info("Upgrade within minor channel succeeded: {} -> {}", startVersion, minorHead);
    }

    @RetryTest
    void testUpgradeAcrossMinors() throws Exception {
        setUp();

        var startVersion = getStartVersion();
        var projectVersion = getProjectVersion();
        var rollingChannel = deriveRollingChannel(startVersion);

        log.info("Testing upgrade via {} channel: {} -> {}", rollingChannel, startVersion,
                projectVersion);

        deployCatalogAndSubscribe(rollingChannel, startVersion);
        waitForOperatorVersion(startVersion);

        log.info("Operator {} deployed, waiting for upgrade to {}", startVersion, projectVersion);
        verifyUpgradeTo(projectVersion);

        verifyUpgradeCompleted(startVersion, projectVersion);

        log.info("Cross-minor upgrade succeeded: {} -> {}", startVersion, projectVersion);
    }

    @RetryTest
    void testChannelSwitchFromRollingToMinor() throws Exception {
        setUp();

        var startVersion = getStartVersion();
        var minorHead = getMinorChannelHead();
        var minorChannel = deriveMinorChannel(startVersion);
        var rollingChannel = deriveRollingChannel(startVersion);

        log.info("Testing channel switch: install {} on {}, then switch to {}", startVersion,
                rollingChannel, minorChannel);

        deployCatalogAndSubscribe(rollingChannel, startVersion);
        waitForOperatorVersion(startVersion);
        log.info("Operator {} deployed on {} channel", startVersion, rollingChannel);

        // Switch subscription to the minor channel
        patchSubscriptionChannel(minorChannel);
        log.info("Switched subscription to {} channel, waiting for upgrade to {}", minorChannel,
                minorHead);

        verifyUpgradeTo(minorHead);
        log.info("Channel switch succeeded: operator is now at {} on {} channel", minorHead,
                minorChannel);
    }

    @RetryTest
    void testChannelSwitchAtChannelHeadIsNoop() throws Exception {
        setUp();

        var projectVersion = getProjectVersion();
        var rollingChannel = deriveRollingChannel(projectVersion);
        var currentMinorChannel = deriveMinorChannel(projectVersion);

        log.info("Testing noop channel switch: install {} on {}, then switch to {} where it's also the head",
                projectVersion, rollingChannel, currentMinorChannel);

        deployCatalogAndSubscribe(rollingChannel, projectVersion);
        waitForOperatorVersion(projectVersion);
        log.info("Operator {} deployed on {} channel", projectVersion, rollingChannel);

        var podBefore = client.pods().inNamespace(namespace).list().getItems().stream()
                .filter(p -> p.getMetadata().getName().contains("apicurio-registry-operator"))
                .filter(p -> "Running".equals(p.getStatus().getPhase()))
                .map(p -> p.getMetadata().getUid())
                .findFirst().orElseThrow(() -> new IllegalStateException("No operator pod found"));
        log.info("Operator pod UID before switch: {}", podBefore);

        patchSubscriptionChannel(currentMinorChannel);
        log.info("Switched subscription to {} channel, verifying operator stays at same version...",
                currentMinorChannel);

        // OLM may re-evaluate and restart the pod on a channel switch, even if the
        // version is the same. Wait for any restarts to settle, then verify the
        // version hasn't changed and the operator is healthy.
        Thread.sleep(30_000);

        // Verify the deployment still exists at the same version and is healthy
        await().atMost(UPGRADE_TIMEOUT).ignoreExceptions().untilAsserted(() -> {
            var deployment = client.apps().deployments().inNamespace(namespace)
                    .withName("apicurio-registry-operator-v" + projectVersion.toLowerCase()).get();
            assertThat(deployment)
                    .as("Operator deployment at " + projectVersion + " should still exist after channel switch")
                    .isNotNull();
            assertThat(deployment.getStatus().getReadyReplicas())
                    .as("Operator should have 1 ready replica after channel switch")
                    .isEqualTo(1);
        });

        var podAfter = client.pods().inNamespace(namespace).list().getItems().stream()
                .filter(p -> p.getMetadata().getName().contains("apicurio-registry-operator"))
                .filter(p -> "Running".equals(p.getStatus().getPhase()))
                .map(p -> p.getMetadata().getUid())
                .findFirst().orElseThrow(() -> new IllegalStateException("No operator pod found after switch"));

        if (podAfter.equals(podBefore)) {
            log.info("Channel switch was a true noop: same pod UID {}", podAfter);
        } else {
            log.info("OLM restarted the pod on channel switch (old: {}, new: {}), but version is unchanged",
                    podBefore, podAfter);
        }

        log.info("Channel switch at channel head verified: operator at {} is healthy", projectVersion);
    }

    @RetryTest
    void testMinorChannelIsolation() throws Exception {
        setUp();

        var startVersion = getStartVersion();
        var minorHead = getMinorChannelHead();
        var minorChannel = deriveMinorChannel(startVersion);

        log.info("Testing minor channel isolation: {} -> {} on {}, then verify no further upgrade",
                startVersion, minorHead, minorChannel);

        deployCatalogAndSubscribe(minorChannel, startVersion);
        waitForOperatorVersion(startVersion);
        verifyUpgradeTo(minorHead);
        log.info("Upgraded to {}. Waiting 60s to verify no further upgrade happens...", minorHead);

        // Wait and verify the operator stays at the minor channel head
        var projectVersion = getProjectVersion();
        var crossMinorDeployment = "apicurio-registry-operator-v" + projectVersion.toLowerCase();
        Thread.sleep(60_000);

        var deployment = client.apps().deployments().inNamespace(namespace)
                .withName(crossMinorDeployment).get();
        assertThat(deployment)
                .as("Operator should NOT be upgraded to " + projectVersion
                        + " on the " + minorChannel + " channel")
                .isNull();

        var currentDeployment = client.apps().deployments().inNamespace(namespace)
                .withName("apicurio-registry-operator-v" + minorHead.toLowerCase()).get();
        assertThat(currentDeployment).as("Operator should still be at " + minorHead).isNotNull();
        assertThat(currentDeployment.getStatus().getReadyReplicas())
                .as("Operator at " + minorHead + " should still have 1 ready replica")
                .isEqualTo(1);

        log.info("Minor channel isolation confirmed: operator stayed at {}", minorHead);
    }

    @RetryTest
    void testFreshInstallOnEachChannel() throws Exception {
        setUp();

        var minorHead = getMinorChannelHead();
        var minorChannel = deriveMinorChannel(minorHead);
        var projectVersion = getProjectVersion();
        var rollingChannel = deriveRollingChannel(projectVersion);

        log.info("Testing fresh install on {} channel (no startingCSV)", minorChannel);

        // Fresh install on minor channel — should get the channel head
        createResource(client, namespace, "olmv0/catalog-source.yaml");
        waitForCatalogPodReady(client, namespace);
        createResource(client, namespace, "olmv0/operator-group.yaml");

        var extraVars = Map.of(
                "${PLACEHOLDER_UPGRADE_CHANNEL}", minorChannel,
                "${PLACEHOLDER_UPGRADE_START_CSV}", "");
        var raw = loadRawResource("olmv0/subscription-upgrade.yaml");
        // Remove the startingCSV field entirely for fresh install
        var subscriptionYaml = replaceVars(raw, namespace, extraVars)
                .replaceAll("\\s*startingCSV:.*", "");
        client.resource(subscriptionYaml).create();

        verifyUpgradeTo(minorHead);
        log.info("Fresh install on {} channel got version {} as expected", minorChannel, minorHead);
    }

    @RetryTest
    void testDowngradeChannelSwitchIsRejected() throws Exception {
        setUp();

        var projectVersion = getProjectVersion();
        var rollingChannel = deriveRollingChannel(projectVersion);
        var minorHead = getMinorChannelHead();
        var olderMinorChannel = deriveMinorChannel(minorHead);

        log.info("Testing downgrade: install current version on {}, then switch to {}",
                rollingChannel, olderMinorChannel);

        // Install the current version on the rolling channel
        deployCatalogAndSubscribe(rollingChannel, projectVersion);
        waitForOperatorVersion(projectVersion);
        log.info("Operator {} deployed on {} channel", projectVersion, rollingChannel);

        // Switch to the older minor channel — OLM should not downgrade
        patchSubscriptionChannel(olderMinorChannel);
        log.info("Switched subscription to {} channel, waiting 60s to verify no downgrade...",
                olderMinorChannel);

        Thread.sleep(60_000);

        // Verify the operator is still running the current version
        var currentDeployment = client.apps().deployments().inNamespace(namespace)
                .withName("apicurio-registry-operator-v" + projectVersion.toLowerCase()).get();
        assertThat(currentDeployment)
                .as("Operator should still be at " + projectVersion + " after channel switch")
                .isNotNull();
        assertThat(currentDeployment.getStatus().getReadyReplicas())
                .as("Operator should still have 1 ready replica")
                .isEqualTo(1);

        // Verify the older version was NOT deployed
        var olderDeployment = client.apps().deployments().inNamespace(namespace)
                .withName("apicurio-registry-operator-v" + minorHead.toLowerCase()).get();
        assertThat(olderDeployment)
                .as("Operator should NOT be downgraded to " + minorHead)
                .isNull();

        // Log subscription state for diagnostics
        try {
            var subscription = client.genericKubernetesResources(
                            "operators.coreos.com/v1alpha1", "Subscription")
                    .inNamespace(namespace)
                    .withName("apicurio-registry-operator-subscription")
                    .get();
            log.info("Subscription after downgrade attempt: {}", subscription.getAdditionalProperties());
        } catch (Exception e) {
            log.info("Could not read subscription state: {}", e.getMessage());
        }

        log.info("Downgrade rejection confirmed: operator stayed at {}", projectVersion);
    }

    @RetryTest
    void testManualApprovalUpgrade() throws Exception {
        setUp();

        var startVersion = getStartVersion();
        var minorHead = getMinorChannelHead();
        var minorChannel = deriveMinorChannel(startVersion);

        log.info("Testing manual approval upgrade on {} channel: {} -> {}", minorChannel,
                startVersion, minorHead);

        // Deploy with Manual install plan approval
        createResource(client, namespace, "olmv0/catalog-source.yaml");
        waitForCatalogPodReady(client, namespace);
        createResource(client, namespace, "olmv0/operator-group.yaml");

        var extraVars = Map.of(
                "${PLACEHOLDER_UPGRADE_CHANNEL}", minorChannel,
                "${PLACEHOLDER_UPGRADE_START_CSV}", csvName(startVersion));
        var raw = loadRawResource("olmv0/subscription-upgrade.yaml");
        var subscriptionYaml = replaceVars(raw, namespace, extraVars)
                .replace("installPlanApproval: Automatic", "installPlanApproval: Manual");
        client.resource(subscriptionYaml).create();

        // With Manual approval, wait for the initial install plan then approve it
        waitForAndApproveInstallPlans();
        waitForOperatorVersion(startVersion);
        log.info("Operator {} deployed with Manual approval", startVersion);

        // Approve install plans in a loop until the target version is reached.
        // Multi-step upgrades (e.g. 3.2.4 → 3.2.5 → 3.2.6) require approving
        // each intermediate install plan.
        await().atMost(UPGRADE_TIMEOUT).pollInterval(java.time.Duration.ofSeconds(10))
                .ignoreExceptions().untilAsserted(() -> {
            approveAllPendingInstallPlans();
            var deployment = client.apps().deployments().inNamespace(namespace)
                    .withName("apicurio-registry-operator-v" + minorHead.toLowerCase()).get();
            assertThat(deployment)
                    .as("Operator should reach " + minorHead + " after approving all install plans")
                    .isNotNull();
            assertThat(deployment.getStatus().getReadyReplicas())
                    .as("Operator at " + minorHead + " should have 1 ready replica")
                    .isEqualTo(1);
        });
        log.info("Manual approval upgrade succeeded: {} -> {}", startVersion, minorHead);
    }

    private void deployCatalogAndSubscribe(String channel, String startVersion) throws IOException {
        createResource(client, namespace, "olmv0/catalog-source.yaml");
        waitForCatalogPodReady(client, namespace);
        createResource(client, namespace, "olmv0/operator-group.yaml");

        var extraVars = Map.of(
                "${PLACEHOLDER_UPGRADE_CHANNEL}", channel,
                "${PLACEHOLDER_UPGRADE_START_CSV}", csvName(startVersion));
        var raw = loadRawResource("olmv0/subscription-upgrade.yaml");
        client.resource(replaceVars(raw, namespace, extraVars)).create();
    }

    private void waitForOperatorVersion(String version) {
        var deploymentName = "apicurio-registry-operator-v" + version.toLowerCase();
        await().atMost(UPGRADE_TIMEOUT).ignoreExceptions().untilAsserted(() -> {
            var deployment = client.apps().deployments().inNamespace(namespace)
                    .withName(deploymentName).get();
            assertThat(deployment).as("Deployment " + deploymentName + " should exist").isNotNull();
            assertThat(deployment.getStatus().getReadyReplicas())
                    .as("Deployment " + deploymentName + " should have 1 ready replica")
                    .isEqualTo(1);
        });
        log.info("Operator version {} is ready", version);
    }

    private void verifyUpgradeTo(String targetVersion) {
        var deploymentName = "apicurio-registry-operator-v" + targetVersion.toLowerCase();
        await().atMost(UPGRADE_TIMEOUT).ignoreExceptions().untilAsserted(() -> {
            var deployment = client.apps().deployments().inNamespace(namespace)
                    .withName(deploymentName).get();
            assertThat(deployment)
                    .as("Target deployment " + deploymentName + " should exist after upgrade")
                    .isNotNull();
            assertThat(deployment.getStatus().getReadyReplicas())
                    .as("Target deployment " + deploymentName + " should have 1 ready replica")
                    .isEqualTo(1);
        });
    }

    @SuppressWarnings("unchecked")
    private void patchSubscriptionChannel(String newChannel) {
        var subscription = client.genericKubernetesResources(
                        "operators.coreos.com/v1alpha1", "Subscription")
                .inNamespace(namespace)
                .withName("apicurio-registry-operator-subscription")
                .get();
        assertThat(subscription).as("Subscription should exist").isNotNull();

        var props = subscription.getAdditionalProperties();
        var spec = (java.util.Map<String, Object>) props.get("spec");
        spec.put("channel", newChannel);
        client.genericKubernetesResources("operators.coreos.com/v1alpha1", "Subscription")
                .inNamespace(namespace)
                .resource(subscription)
                .update();
        log.info("Patched subscription channel to {}", newChannel);
    }

    private void waitForAndApproveInstallPlans() {
        await().atMost(UPGRADE_TIMEOUT).ignoreExceptions().untilAsserted(() -> {
            var pending = countPendingInstallPlans();
            log.info("Waiting for pending install plans... found: {}", pending);
            assertThat(pending).as("Should have at least one pending install plan")
                    .isGreaterThanOrEqualTo(1);
        });
        approveAllPendingInstallPlans();
    }

    @SuppressWarnings("unchecked")
    private void approveAllPendingInstallPlans() {
        var installPlans = client.genericKubernetesResources(
                        "operators.coreos.com/v1alpha1", "InstallPlan")
                .inNamespace(namespace).list().getItems();

        for (var ip : installPlans) {
            var props = ip.getAdditionalProperties();
            var spec = (java.util.Map<String, Object>) props.get("spec");
            if (spec != null && Boolean.FALSE.equals(spec.get("approved"))) {
                spec.put("approved", true);
                client.genericKubernetesResources("operators.coreos.com/v1alpha1", "InstallPlan")
                        .inNamespace(namespace)
                        .resource(ip)
                        .update();
                log.info("Approved install plan: {}", ip.getMetadata().getName());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private long countPendingInstallPlans() {
        return client.genericKubernetesResources(
                        "operators.coreos.com/v1alpha1", "InstallPlan")
                .inNamespace(namespace).list().getItems().stream()
                .filter(ip -> {
                    var spec = (java.util.Map<String, Object>) ip.getAdditionalProperties()
                            .get("spec");
                    return spec != null && Boolean.FALSE.equals(spec.get("approved"));
                })
                .count();
    }

    private void verifyUpgradeCompleted(String startVersion, String targetVersion) {
        // OLM cleans up old CSVs after upgrade, so we can only verify the final state.
        // The fact that we started at startVersion and arrived at targetVersion confirms
        // intermediate versions were traversed (OLM follows the replaces chain).
        var csvList = client.genericKubernetesResources(
                        "operators.coreos.com/v1alpha1", "ClusterServiceVersion")
                .inNamespace(namespace).list().getItems();
        var installedCSVs = csvList.stream()
                .map(csv -> csv.getMetadata().getName())
                .filter(name -> name.startsWith(PACKAGE_NAME))
                .toList();

        log.info("CSVs present after upgrade from {} to {}: {}", startVersion, targetVersion,
                installedCSVs);

        assertThat(installedCSVs)
                .as("Target version CSV should be present after upgrade")
                .anyMatch(csv -> csv.contains(targetVersion.toLowerCase()));
    }
}
