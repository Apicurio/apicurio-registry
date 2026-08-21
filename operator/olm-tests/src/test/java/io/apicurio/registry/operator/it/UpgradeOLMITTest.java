package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.utils.ClusterDiagnostics;
import io.apicurio.registry.operator.utils.OperatorTestContext;
import io.apicurio.registry.operator.utils.OperatorTestExtension;
import io.apicurio.registry.operator.utils.RetryTest;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.extension.ExtendWith;
import org.semver4j.Semver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.awaitility.core.ConditionFactory;

import static io.apicurio.registry.operator.Tags.OLM;
import static io.apicurio.registry.operator.it.ITBase.setDefaultAwaitilityTimings;
import static io.apicurio.registry.operator.it.OLMTestUtils.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Tests OLM upgrade paths using OLM v0 (Subscription model).
 * <p>
 * Discovers available versions from the live catalog's PackageManifest at runtime, so these tests
 * work with both upstream catalogs (built from catalog.template.yaml) and downstream IIB images
 * (where CSV names have productized suffixes). See the olm-tests README for which tests apply
 * in each release scenario.
 */
@QuarkusTest
@Tag(OLM)
@DisabledIfSystemProperty(named = OLMTestUtils.OLM_VERSION_PROP, matches = "1",
        disabledReason = "Upgrade tests use OLM v0 Subscriptions which are not available on OLM v1")
@ExtendWith(OperatorTestExtension.class)
public class UpgradeOLMITTest implements OperatorTestContext {

    private static final Logger log = LoggerFactory.getLogger(UpgradeOLMITTest.class);

    private static final Duration UPGRADE_TIMEOUT = Duration.ofSeconds(
            Integer.getInteger("test.operator.timeout.olm-upgrade", 900));

    private static final String SUBSCRIPTION_NAME = "apicurio-registry-operator-subscription";

    private static CatalogInfo catalog;

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

    @BeforeAll
    static void discoverCatalog() throws Exception {
        setDefaultAwaitilityTimings();
        var discoveryClient = ITBase.createK8sClient("default");
        try {
            catalog = CatalogDiscovery.getInstance(discoveryClient).getCatalogInfo();
        } finally {
            discoveryClient.close();
        }
    }

    private static String projectVersion() {
        return OLMTestUtils.getProjectVersion();
    }

    private static String minorChannel() {
        return deriveMinorChannel(projectVersion());
    }

    private static String rollingChannel() {
        return deriveRollingChannel(projectVersion());
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

    // ---- Condition methods for @EnabledIf ----

    static boolean minorChannelHasPreviousEntry() {
        var has = catalog.getPreviousEntry(minorChannel()) != null;
        if (!has) {
            log.info("Condition not met: channel {} has fewer than 2 entries (first-in-minor release)",
                    minorChannel());
        }
        return has;
    }

    static boolean isHeadOfRollingChannel() {
        var is = catalog.isVersionChannelHead(rollingChannel(), projectVersion());
        if (!is) {
            log.info("Condition not met: version {} is not the head of {} (maintenance branch release?)",
                    projectVersion(), rollingChannel());
        }
        return is;
    }

    static boolean hasCrossMinorEntryInRollingChannel() {
        if (!isHeadOfRollingChannel()) {
            return false;
        }
        var entry = catalog.getCrossMinorEntry(rollingChannel());
        if (entry == null) {
            log.info("Condition not met: no cross-minor entry found in {}", rollingChannel());
            return false;
        }
        return true;
    }

    static boolean isInRollingAndMinorChannel() {
        var inRolling = catalog.isVersionChannelHead(rollingChannel(), projectVersion());
        var hasMinor = catalog.hasChannel(minorChannel());
        if (!inRolling) {
            log.info("Condition not met: version {} is not the head of {}",
                    projectVersion(), rollingChannel());
        }
        if (!hasMinor) {
            log.info("Condition not met: channel {} not found in catalog", minorChannel());
        }
        return inRolling && hasMinor;
    }

    static boolean rollingChannelHasPreviousEntry() {
        if (!isInRollingAndMinorChannel()) {
            return false;
        }
        var has = catalog.getPreviousEntry(rollingChannel()) != null;
        if (!has) {
            log.info("Condition not met: {} has fewer than 2 entries", rollingChannel());
        }
        return has;
    }

    static boolean hasOlderMinorChannel() {
        if (!isHeadOfRollingChannel()) {
            return false;
        }
        var ch = catalog.getOlderMinorChannel(projectVersion(), rollingChannel());
        if (ch == null) {
            log.info("Condition not met: no older minor channel found in catalog");
            return false;
        }
        return true;
    }

    static boolean minorChannelExists() {
        var exists = catalog.hasChannel(minorChannel());
        if (!exists) {
            log.info("Condition not met: channel {} not found in catalog", minorChannel());
        }
        return exists;
    }

    // ---- Test methods ----

    /**
     * Verifies patch upgrade within the current minor channel (e.g., 3.3.0 -> 3.3.1 on 3.3.x).
     * Requires at least 2 entries in the minor channel.
     */
    @RetryTest
    @EnabledIf("minorChannelHasPreviousEntry")
    void testUpgradeWithinMinorChannel() throws Exception {
        setUp();

        var prev = catalog.getPreviousEntry(minorChannel());
        var headVersion = catalog.getChannelHeadVersion(minorChannel());

        log.info("Testing upgrade within {} channel: {} -> {}",
                minorChannel(), prev.getVersion(), headVersion);

        deployCatalogAndSubscribe(minorChannel(), prev.getCsvName());
        waitForOperatorVersion(prev.getVersion());

        log.info("Operator {} deployed, waiting for upgrade to {}", prev.getVersion(), headVersion);
        verifyUpgradeTo(headVersion);

        log.info("Upgrade within minor channel succeeded: {} -> {}", prev.getVersion(), headVersion);
    }

    /**
     * Verifies cross-minor upgrade via the rolling channel (e.g., 3.2.5 -> 3.3.1 on 3.x).
     * Requires the current version to be in the 3.x channel and an entry from a different minor.
     */
    @RetryTest
    @EnabledIf("hasCrossMinorEntryInRollingChannel")
    void testUpgradeAcrossMinors() throws Exception {
        setUp();

        var crossMinorEntry = catalog.getCrossMinorEntry(rollingChannel());
        var headVersion = catalog.getChannelHeadVersion(rollingChannel());

        log.info("Testing upgrade via {} channel: {} -> {}",
                rollingChannel(), crossMinorEntry.getVersion(), headVersion);

        deployCatalogAndSubscribe(rollingChannel(), crossMinorEntry.getCsvName());
        waitForOperatorVersion(crossMinorEntry.getVersion());

        log.info("Operator {} deployed, waiting for upgrade to {}",
                crossMinorEntry.getVersion(), headVersion);
        verifyUpgradeTo(headVersion);

        verifyUpgradeCompleted(crossMinorEntry.getVersion(), headVersion);

        log.info("Cross-minor upgrade succeeded: {} -> {}",
                crossMinorEntry.getVersion(), headVersion);
    }

    /**
     * Verifies channel switch from rolling to minor channel. Installs on 3.x, then switches
     * subscription to 3.3.x and verifies the operator reaches the minor channel head.
     * Requires the current version to be in both channels and a previous entry in the rolling channel.
     */
    @RetryTest
    @EnabledIf("rollingChannelHasPreviousEntry")
    void testChannelSwitchFromRollingToMinor() throws Exception {
        setUp();

        var prev = catalog.getPreviousEntry(rollingChannel());
        var minorHeadVersion = catalog.getChannelHeadVersion(minorChannel());

        log.info("Testing channel switch: install {} on {}, then switch to {}",
                prev.getVersion(), rollingChannel(), minorChannel());

        deployCatalogAndSubscribe(rollingChannel(), prev.getCsvName());
        waitForOperatorVersion(prev.getVersion());
        log.info("Operator {} deployed on {} channel", prev.getVersion(), rollingChannel());

        patchSubscriptionChannel(minorChannel());
        log.info("Switched subscription to {} channel, waiting for upgrade to {}",
                minorChannel(), minorHeadVersion);

        verifyUpgradeTo(minorHeadVersion);
        log.info("Channel switch succeeded: operator is now at {} on {} channel",
                minorHeadVersion, minorChannel());
    }

    /**
     * Verifies that switching channels at the channel head is a no-op (operator stays at same version).
     * Requires the current version to be in both 3.x and its minor channel.
     */
    @RetryTest
    @EnabledIf("isInRollingAndMinorChannel")
    void testChannelSwitchAtChannelHeadIsNoop() throws Exception {
        setUp();

        var headCSV = catalog.getChannelHeadCSV(rollingChannel());
        var headVersion = catalog.getChannelHeadVersion(rollingChannel());

        log.info("Testing noop channel switch: install {} on {}, then switch to {}",
                headVersion, rollingChannel(), minorChannel());

        deployCatalogAndSubscribe(rollingChannel(), headCSV);
        waitForOperatorVersion(headVersion);
        log.info("Operator {} deployed on {} channel", headVersion, rollingChannel());

        var podBefore = client.pods().inNamespace(namespace).list().getItems().stream()
                .filter(p -> p.getMetadata().getName().contains("apicurio-registry-operator"))
                .filter(p -> "Running".equals(p.getStatus().getPhase()))
                .map(p -> p.getMetadata().getUid())
                .findFirst().orElseThrow(() -> new IllegalStateException("No operator pod found"));
        log.info("Operator pod UID before switch: {}", podBefore);

        patchSubscriptionChannel(minorChannel());
        log.info("Switched subscription to {} channel, verifying operator stays at same version...",
                minorChannel());

        Thread.sleep(30_000);

        upgradeAwait().untilAsserted(() -> {
            var deployment = client.apps().deployments().inNamespace(namespace)
                    .withName(deploymentName(headVersion)).get();
            assertThat(deployment)
                    .as("Operator deployment at " + headVersion
                            + " should still exist after channel switch")
                    .isNotNull();
            assertThat(deployment.getStatus().getReadyReplicas())
                    .as("Operator should have 1 ready replica after channel switch")
                    .isEqualTo(1);
        });

        var podAfter = client.pods().inNamespace(namespace).list().getItems().stream()
                .filter(p -> p.getMetadata().getName().contains("apicurio-registry-operator"))
                .filter(p -> "Running".equals(p.getStatus().getPhase()))
                .map(p -> p.getMetadata().getUid())
                .findFirst().orElseThrow(
                        () -> new IllegalStateException("No operator pod found after switch"));

        if (podAfter.equals(podBefore)) {
            log.info("Channel switch was a true noop: same pod UID {}", podAfter);
        } else {
            log.info("OLM restarted the pod on channel switch (old: {}, new: {}), "
                    + "but version is unchanged", podBefore, podAfter);
        }

        log.info("Channel switch at channel head verified: operator at {} is healthy", headVersion);
    }

    /**
     * Verifies minor channel isolation — upgrade within the minor channel should not cross minor
     * boundaries. Requires at least 2 entries in the minor channel.
     */
    @RetryTest
    @EnabledIf("minorChannelHasPreviousEntry")
    void testMinorChannelIsolation() throws Exception {
        setUp();

        var prev = catalog.getPreviousEntry(minorChannel());
        var minorHeadVersion = catalog.getChannelHeadVersion(minorChannel());

        log.info("Testing minor channel isolation: {} -> {} on {}, then verify no further upgrade",
                prev.getVersion(), minorHeadVersion, minorChannel());

        deployCatalogAndSubscribe(minorChannel(), prev.getCsvName());
        waitForOperatorVersion(prev.getVersion());
        verifyUpgradeTo(minorHeadVersion);
        log.info("Upgraded to {}. Waiting 60s to verify no further upgrade happens...",
                minorHeadVersion);

        var rollingHeadVersion = catalog.getChannelHeadVersion(rollingChannel());
        if (rollingHeadVersion != null && !rollingHeadVersion.equals(minorHeadVersion)) {
            Thread.sleep(60_000);

            var deployment = client.apps().deployments().inNamespace(namespace)
                    .withName(deploymentName(rollingHeadVersion)).get();
            assertThat(deployment)
                    .as("Operator should NOT be upgraded to " + rollingHeadVersion
                            + " on the " + minorChannel() + " channel")
                    .isNull();
        }

        var currentDeployment = client.apps().deployments().inNamespace(namespace)
                .withName(deploymentName(minorHeadVersion)).get();
        assertThat(currentDeployment)
                .as("Operator should still be at " + minorHeadVersion).isNotNull();
        assertThat(currentDeployment.getStatus().getReadyReplicas())
                .as("Operator at " + minorHeadVersion + " should still have 1 ready replica")
                .isEqualTo(1);

        log.info("Minor channel isolation confirmed: operator stayed at {}", minorHeadVersion);
    }

    /**
     * Verifies fresh install on the minor channel (no startingCSV) gets the channel head.
     * Requires the minor channel to exist.
     */
    @RetryTest
    @EnabledIf("minorChannelExists")
    void testFreshInstallOnEachChannel() throws Exception {
        setUp();

        var minorHeadVersion = catalog.getChannelHeadVersion(minorChannel());

        log.info("Testing fresh install on {} channel (no startingCSV)", minorChannel());

        createResource(client, namespace, "olmv0/catalog-source.yaml");
        waitForCatalogPodReady(client, namespace);
        createResource(client, namespace, "olmv0/operator-group.yaml");

        var extraVars = Map.of(
                "${PLACEHOLDER_UPGRADE_CHANNEL}", minorChannel(),
                "${PLACEHOLDER_UPGRADE_START_CSV}", "");
        var raw = loadRawResource("olmv0/subscription-upgrade.yaml");
        var subscriptionYaml = replaceVars(raw, namespace, extraVars)
                .replaceAll("\\s*startingCSV:.*", "");
        client.resource(subscriptionYaml).create();

        verifyUpgradeTo(minorHeadVersion);
        log.info("Fresh install on {} channel got version {} as expected",
                minorChannel(), minorHeadVersion);
    }

    /**
     * Verifies that switching to an older minor channel does not downgrade the operator.
     * Requires the current version to be in the rolling channel and an older minor channel to exist.
     * The older channel is selected by parsed minor version (highest minor < current).
     */
    @RetryTest
    @EnabledIf("hasOlderMinorChannel")
    void testDowngradeChannelSwitchIsRejected() throws Exception {
        setUp();

        var headCSV = catalog.getChannelHeadCSV(rollingChannel());
        var headVersion = catalog.getChannelHeadVersion(rollingChannel());
        var olderMinorChannel = catalog.getOlderMinorChannel(projectVersion(), rollingChannel());
        var olderHeadVersion = catalog.getChannelHeadVersion(olderMinorChannel);

        log.info("Testing downgrade: install current version on {}, then switch to {}",
                rollingChannel(), olderMinorChannel);

        deployCatalogAndSubscribe(rollingChannel(), headCSV);
        waitForOperatorVersion(headVersion);
        log.info("Operator {} deployed on {} channel", headVersion, rollingChannel());

        patchSubscriptionChannel(olderMinorChannel);
        log.info("Switched subscription to {} channel, waiting 60s to verify no downgrade...",
                olderMinorChannel);

        Thread.sleep(60_000);

        var currentDeployment = client.apps().deployments().inNamespace(namespace)
                .withName(deploymentName(headVersion)).get();
        assertThat(currentDeployment)
                .as("Operator should still be at " + headVersion + " after channel switch")
                .isNotNull();
        assertThat(currentDeployment.getStatus().getReadyReplicas())
                .as("Operator should still have 1 ready replica")
                .isEqualTo(1);

        var olderDeployment = client.apps().deployments().inNamespace(namespace)
                .withName(deploymentName(olderHeadVersion)).get();
        assertThat(olderDeployment)
                .as("Operator should NOT be downgraded to " + olderHeadVersion)
                .isNull();

        log.info("Downgrade rejection confirmed: operator stayed at {}", headVersion);
    }

    /**
     * Verifies upgrade with manual install plan approval. Requires at least 2 entries in the
     * minor channel.
     */
    @RetryTest
    @EnabledIf("minorChannelHasPreviousEntry")
    void testManualApprovalUpgrade() throws Exception {
        setUp();

        var prev = catalog.getPreviousEntry(minorChannel());
        var minorHeadVersion = catalog.getChannelHeadVersion(minorChannel());

        log.info("Testing manual approval upgrade on {} channel: {} -> {}",
                minorChannel(), prev.getVersion(), minorHeadVersion);

        createResource(client, namespace, "olmv0/catalog-source.yaml");
        waitForCatalogPodReady(client, namespace);
        createResource(client, namespace, "olmv0/operator-group.yaml");

        var extraVars = Map.of(
                "${PLACEHOLDER_UPGRADE_CHANNEL}", minorChannel(),
                "${PLACEHOLDER_UPGRADE_START_CSV}", prev.getCsvName());
        var raw = loadRawResource("olmv0/subscription-upgrade.yaml");
        var subscriptionYaml = replaceVars(raw, namespace, extraVars)
                .replace("installPlanApproval: Automatic", "installPlanApproval: Manual");
        client.resource(subscriptionYaml).create();

        waitForAndApproveInstallPlans();
        waitForOperatorVersion(prev.getVersion());
        log.info("Operator {} deployed with Manual approval", prev.getVersion());

        upgradeAwait().pollInterval(java.time.Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    approveAllPendingInstallPlans();
                    var deployment = client.apps().deployments().inNamespace(namespace)
                            .withName(deploymentName(minorHeadVersion)).get();
                    assertThat(deployment)
                            .as("Operator should reach " + minorHeadVersion
                                    + " after approving all install plans")
                            .isNotNull();
                    assertThat(deployment.getStatus().getReadyReplicas())
                            .as("Operator at " + minorHeadVersion + " should have 1 ready replica")
                            .isEqualTo(1);
                });
        log.info("Manual approval upgrade succeeded: {} -> {}", prev.getVersion(), minorHeadVersion);
    }

    // ---- Infrastructure methods ----

    private static String deploymentName(Semver version) {
        return "apicurio-registry-operator-v" + version;
    }

    private void deployCatalogAndSubscribe(String channel, String startCSV) throws Exception {
        try {
            createResource(client, namespace, "olmv0/catalog-source.yaml");
            waitForCatalogPodReady(client, namespace);
            createResource(client, namespace, "olmv0/operator-group.yaml");

            var extraVars = Map.of(
                    "${PLACEHOLDER_UPGRADE_CHANNEL}", channel,
                    "${PLACEHOLDER_UPGRADE_START_CSV}", startCSV);
            var raw = loadRawResource("olmv0/subscription-upgrade.yaml");
            client.resource(replaceVars(raw, namespace, extraVars)).create();
        } catch (Exception e) {
            log.error("OLM catalog/subscription setup failed, dumping cluster diagnostics", e);
            ClusterDiagnostics.dump(client, namespace, true);
            throw e;
        }
    }

    private void waitForOperatorVersion(Semver version) {
        var name = deploymentName(version);
        upgradeAwait().untilAsserted(() -> {
            var deployment = client.apps().deployments().inNamespace(namespace)
                    .withName(name).get();
            assertThat(deployment).as("Deployment " + name + " should exist").isNotNull();
            assertThat(deployment.getStatus().getReadyReplicas())
                    .as("Deployment " + name + " should have 1 ready replica")
                    .isEqualTo(1);
        });
        log.info("Operator version {} is ready", version);
    }

    private void verifyUpgradeTo(Semver targetVersion) {
        var name = deploymentName(targetVersion);
        upgradeAwait().untilAsserted(() -> {
            var deployment = client.apps().deployments().inNamespace(namespace)
                    .withName(name).get();
            assertThat(deployment)
                    .as("Target deployment " + name + " should exist after upgrade")
                    .isNotNull();
            assertThat(deployment.getStatus().getReadyReplicas())
                    .as("Target deployment " + name + " should have 1 ready replica")
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

    // Awaitility factory for upgrade waits. Fails fast only on terminal resolution
    // failures (unsatisfiable constraints never produce an install plan, so without
    // this every such failure burns the whole UPGRADE_TIMEOUT). Transient states like
    // ErrorPreventedResolution (catalog service not routable yet) recover on their own.
    private ConditionFactory upgradeAwait() {
        return await().atMost(UPGRADE_TIMEOUT).ignoreExceptions()
                .failFast("Subscription resolution failed", this::subscriptionResolutionFailed);
    }

    @SuppressWarnings("unchecked")
    private boolean subscriptionResolutionFailed() {
        try {
            var sub = client.genericKubernetesResources("operators.coreos.com/v1alpha1", "Subscription")
                    .inNamespace(namespace).withName(SUBSCRIPTION_NAME).get();
            if (sub == null) {
                return false;
            }
            var conditions = (List<Map<String, Object>>) sub.get("status", "conditions");
            if (conditions == null) {
                return false;
            }
            for (var condition : conditions) {
                if ("ResolutionFailed".equals(condition.get("type"))
                        && "True".equals(condition.get("status"))
                        && "ConstraintsNotSatisfiable".equals(condition.get("reason"))) {
                    log.error("Subscription {} ConstraintsNotSatisfiable: {}", SUBSCRIPTION_NAME,
                            condition.get("message"));
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            // Transient API errors must not trip the fail-fast check
            return false;
        }
    }

    private void waitForAndApproveInstallPlans() {
        upgradeAwait().untilAsserted(() -> {
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

    private void verifyUpgradeCompleted(Semver startVersion, Semver targetVersion) {
        var csvList = client.genericKubernetesResources(
                        "operators.coreos.com/v1alpha1", "ClusterServiceVersion")
                .inNamespace(namespace).list().getItems();
        var installedCSVs = csvList.stream()
                .map(csv -> csv.getMetadata().getName())
                .filter(name -> name.startsWith(PACKAGE_NAME))
                .toList();

        log.info("CSVs present after upgrade from {} to {}: {}",
                startVersion, targetVersion, installedCSVs);

        assertThat(installedCSVs)
                .as("Target version CSV should be present after upgrade")
                .anyMatch(csv -> csv.contains(targetVersion.toString()));
    }
}
