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
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import org.semver4j.Semver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static io.apicurio.registry.operator.Tags.OLM;
import static io.apicurio.registry.operator.it.ITBase.MEDIUM_DURATION;
import static io.apicurio.registry.operator.it.ITBase.SHORT_DURATION;
import static io.apicurio.registry.operator.it.ITBase.setDefaultAwaitilityTimings;
import static io.apicurio.registry.operator.it.OLMTestUtils.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Tests OLM upgrade paths using OLM v1 ({@code ClusterExtension}). This is the OLM v1 counterpart of
 * {@link UpgradeOLMITTest}, covering every scenario there except manual-approval upgrade: OLM v1's
 * ClusterExtension has no InstallPlan/approval concept. Resolution is either automatic (whatever version
 * satisfies {@code spec.source.catalog.version} at reconcile time) or explicit user-driven (edit the
 * constraint yourself), with no intermediate "pending approval" object to approve. See
 * {@link UpgradeOLMITTest#testManualApprovalUpgrade} for the OLM v0 equivalent, which remains v0-only.
 * <p>
 * Unlike OLM v0's Subscription (which always auto-resolves to the newest CSV in its channel), a
 * ClusterExtension additionally pins an explicit version constraint. So "upgrade" here means patching
 * {@code spec.source.catalog.version} (and {@code spec.source.catalog.channels} for channel switches) on
 * the ClusterExtension and waiting for the operator deployment to follow — there is no separate trigger.
 * <p>
 * Catalog discovery (channels, versions, heads) is shared with {@link UpgradeOLMITTest} via
 * {@link CatalogDiscovery}, which reads catalogd's File-Based Catalog content instead of exec'ing into
 * a catalog pod when {@code test.operator.olm-version=1}. Both OLM versions resolve to the same
 * {@link CatalogInfo} model, so the channel/version query logic below is identical to
 * {@link UpgradeOLMITTest}'s.
 */
@QuarkusTest
@Tag(OLM)
@EnabledIfSystemProperty(named = OLMTestUtils.OLM_VERSION_PROP, matches = "1")
@ExtendWith(OperatorTestExtension.class)
public class UpgradeOLMv1ITTest implements OperatorTestContext {

    private static final Logger log = LoggerFactory.getLogger(UpgradeOLMv1ITTest.class);

    private static final String CLUSTER_EXTENSION_NAME = "apicurio-registry-operator-ce";

    private static final String OPERATOR_DEPLOYMENT_PREFIX = "apicurio-registry-operator-v";

    // The first operator release whose bundle ships the cluster-tier CSV RBAC an OLM v1
    // ClusterExtension install needs (apicurio-registry#9035). Older bundles rely on an OLM v0
    // OperatorGroup to scope the operator's watch; ClusterExtension has no equivalent, so they
    // crash-loop on a forbidden cluster-scoped list when installed via OLM v1. Cross-minor upgrade
    // coverage from those releases stays OLM v0-only (see UpgradeOLMITTest#testUpgradeAcrossMinors).
    private static final String MIN_OLM_V1_OPERATOR_VERSION = "3.3.2";

    private static final Duration UPGRADE_TIMEOUT = Duration.ofSeconds(
            Integer.getInteger("test.operator.timeout.olm-upgrade", 1200));

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
                deleteResourceQuietly(client, namespace, "olmv1/cluster-extension-upgrade.yaml");
                waitForClusterScopedDeleted("ClusterExtension", CLUSTER_EXTENSION_NAME);
                deleteResourceQuietly(client, namespace, "olmv1/cluster-role-binding.yaml");
                deleteResourceQuietly(client, namespace, "olmv1/cluster-role.yaml");
                deleteResourceQuietly(client, namespace, "olmv1/service-account.yaml");
                deleteResourceQuietly(client, namespace, "olmv1/cluster-catalog.yaml");
                waitForClusterScopedDeleted("ClusterCatalog", CATALOG_NAME);
                client.namespaces().withName(namespace).delete();
            } catch (Exception e) {
                log.warn("Cleanup error: {}", e.getMessage());
            }
            client.close();
        }
    }

    // ClusterExtension/ClusterCatalog/ClusterRole(Binding) are cluster-scoped with fixed names, so the
    // next test's setup would collide with a still-terminating resource from this one unless we wait.
    private void waitForClusterScopedDeleted(String kind, String name) {
        await().atMost(SHORT_DURATION).ignoreExceptions().until(() -> client
                .genericKubernetesResources("olm.operatorframework.io/v1", kind)
                .inNamespace(namespace).withName(name).get() == null);
    }

    // ---- Condition methods for @EnabledIf (mirrors UpgradeOLMITTest; kept per-class since each class
    // discovers into its own static `catalog` field) ----

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
        if (entry.getVersion().isLowerThan(MIN_OLM_V1_OPERATOR_VERSION)) {
            log.info("Condition not met: cross-minor entry {} in {} predates OLM v1 bundle support "
                    + "({}); cross-minor upgrade from it is covered by UpgradeOLMITTest (OLM v0)",
                    entry.getVersion(), rollingChannel(), MIN_OLM_V1_OPERATOR_VERSION);
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
     * Verifies patch upgrade within the current minor channel (e.g., 3.3.0 -> 3.3.1 on 3.3.x), driven by
     * patching {@code spec.source.catalog.version} on the ClusterExtension.
     */
    @RetryTest
    @EnabledIf("minorChannelHasPreviousEntry")
    void testUpgradeWithinMinorChannel() throws Exception {
        setUp();

        var prev = catalog.getPreviousEntry(minorChannel());
        var headVersion = catalog.getChannelHeadVersion(minorChannel());

        log.info("Testing upgrade within {} channel: {} -> {}",
                minorChannel(), prev.getVersion(), headVersion);

        deployClusterExtension(minorChannel(), prev.getVersion());
        waitForOperatorVersion(prev.getVersion());

        log.info("Operator {} deployed, patching version constraint to {}", prev.getVersion(),
                headVersion);
        patchClusterExtensionSource(minorChannel(), headVersion);
        verifyUpgradeTo(headVersion);

        log.info("Upgrade within minor channel succeeded: {} -> {}", prev.getVersion(), headVersion);
    }

    /**
     * Verifies cross-minor upgrade via the rolling channel (e.g., 3.2.5 -> 3.3.1 on 3.x).
     */
    @RetryTest
    @EnabledIf("hasCrossMinorEntryInRollingChannel")
    void testUpgradeAcrossMinors() throws Exception {
        setUp();

        var crossMinorEntry = catalog.getCrossMinorEntry(rollingChannel());
        var headVersion = catalog.getChannelHeadVersion(rollingChannel());

        log.info("Testing upgrade via {} channel: {} -> {}",
                rollingChannel(), crossMinorEntry.getVersion(), headVersion);

        deployClusterExtension(rollingChannel(), crossMinorEntry.getVersion());
        waitForOperatorVersion(crossMinorEntry.getVersion());

        log.info("Operator {} deployed, patching version constraint to {}",
                crossMinorEntry.getVersion(), headVersion);
        patchClusterExtensionSource(rollingChannel(), headVersion);
        verifyUpgradeTo(headVersion);

        log.info("Cross-minor upgrade succeeded: {} -> {}", crossMinorEntry.getVersion(), headVersion);
    }

    /**
     * Verifies channel switch from rolling to minor channel. Installs on 3.x, then switches the
     * ClusterExtension to 3.3.x (patching both channel and version, since a v1 exact version pin does not
     * auto-follow a channel switch the way a v0 Subscription's channel-only field does) and verifies the
     * operator reaches the minor channel head.
     */
    @RetryTest
    @EnabledIf("rollingChannelHasPreviousEntry")
    void testChannelSwitchFromRollingToMinor() throws Exception {
        setUp();

        var prev = catalog.getPreviousEntry(rollingChannel());
        var minorHeadVersion = catalog.getChannelHeadVersion(minorChannel());

        log.info("Testing channel switch: install {} on {}, then switch to {}",
                prev.getVersion(), rollingChannel(), minorChannel());

        deployClusterExtension(rollingChannel(), prev.getVersion());
        waitForOperatorVersion(prev.getVersion());
        log.info("Operator {} deployed on {} channel", prev.getVersion(), rollingChannel());

        patchClusterExtensionSource(minorChannel(), minorHeadVersion);
        log.info("Switched ClusterExtension to {} channel, waiting for upgrade to {}",
                minorChannel(), minorHeadVersion);

        verifyUpgradeTo(minorHeadVersion);
        log.info("Channel switch succeeded: operator is now at {} on {} channel",
                minorHeadVersion, minorChannel());
    }

    /**
     * Verifies that switching channels at the channel head is a no-op (operator stays at same version).
     */
    @RetryTest
    @EnabledIf("isInRollingAndMinorChannel")
    void testChannelSwitchAtChannelHeadIsNoop() throws Exception {
        setUp();

        var headVersion = catalog.getChannelHeadVersion(rollingChannel());

        log.info("Testing noop channel switch: install {} on {}, then switch to {}",
                headVersion, rollingChannel(), minorChannel());

        deployClusterExtension(rollingChannel(), headVersion);
        waitForOperatorVersion(headVersion);
        log.info("Operator {} deployed on {} channel", headVersion, rollingChannel());

        var podBefore = client.pods().inNamespace(namespace).list().getItems().stream()
                .filter(p -> p.getMetadata().getName().contains("apicurio-registry-operator"))
                .filter(p -> "Running".equals(p.getStatus().getPhase()))
                .map(p -> p.getMetadata().getUid())
                .findFirst().orElseThrow(() -> new IllegalStateException("No operator pod found"));
        log.info("Operator pod UID before switch: {}", podBefore);

        patchClusterExtensionSource(minorChannel(), headVersion);
        log.info("Switched ClusterExtension to {} channel (same version), verifying operator stays "
                + "at same version...", minorChannel());

        await().atMost(UPGRADE_TIMEOUT).during(Duration.ofSeconds(20)).ignoreExceptions()
                .untilAsserted(() -> {
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
     * Verifies minor channel isolation: upgrading within the minor channel should not cross minor
     * boundaries.
     */
    @RetryTest
    @EnabledIf("minorChannelHasPreviousEntry")
    void testMinorChannelIsolation() throws Exception {
        setUp();

        var prev = catalog.getPreviousEntry(minorChannel());
        var minorHeadVersion = catalog.getChannelHeadVersion(minorChannel());

        log.info("Testing minor channel isolation: {} -> {} on {}, then verify no further upgrade",
                prev.getVersion(), minorHeadVersion, minorChannel());

        deployClusterExtension(minorChannel(), prev.getVersion());
        waitForOperatorVersion(prev.getVersion());
        patchClusterExtensionSource(minorChannel(), minorHeadVersion);
        verifyUpgradeTo(minorHeadVersion);
        log.info("Upgraded to {}. Verifying no further upgrade happens...", minorHeadVersion);

        var rollingHeadVersion = catalog.getChannelHeadVersion(rollingChannel());
        if (rollingHeadVersion != null && !rollingHeadVersion.equals(minorHeadVersion)) {
            await().atMost(MEDIUM_DURATION).during(Duration.ofSeconds(30)).ignoreExceptions()
                    .untilAsserted(() -> {
                        var deployment = client.apps().deployments().inNamespace(namespace)
                                .withName(deploymentName(rollingHeadVersion)).get();
                        assertThat(deployment)
                                .as("Operator should NOT be upgraded to " + rollingHeadVersion
                                        + " on the " + minorChannel() + " channel")
                                .isNull();
                    });
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
     * Verifies fresh install on the minor channel lands on the channel head.
     * <p>
     * Like the OLM v0 equivalent (which omits {@code startingCSV} and relies on the Subscription's
     * always-resolve-to-newest-in-channel behavior), this deploys a channel-only {@code ClusterExtension}
     * with no {@code spec.source.catalog.version} and lets OLM v1 resolve the version itself. The test
     * then reads the version OLM actually installed (from the ready operator deployment) and asserts it
     * equals the discovered channel head, genuinely exercising OLM v1's default channel-head resolution.
     */
    @RetryTest
    @EnabledIf("minorChannelExists")
    void testFreshInstallOnEachChannel() throws Exception {
        setUp();

        var minorHeadVersion = catalog.getChannelHeadVersion(minorChannel());

        log.info("Testing fresh install on {} channel with a version-less ClusterExtension "
                + "(expecting OLM to resolve to channel head {})", minorChannel(), minorHeadVersion);

        deployClusterExtensionChannelOnly(minorChannel());

        var resolvedVersion = waitForResolvedOperatorVersion();
        log.info("Version-less ClusterExtension on {} resolved to {} (channel head is {})",
                minorChannel(), resolvedVersion, minorHeadVersion);

        assertThat(resolvedVersion)
                .as("A version-less ClusterExtension on channel %s should resolve to the channel head",
                        minorChannel())
                .isEqualTo(minorHeadVersion);
    }

    /**
     * Verifies that switching to an older minor channel does not downgrade the operator: the version
     * constraint is deliberately left pinned to the current head (which does not exist in the older
     * channel), so resolution should fail rather than silently downgrading.
     */
    @RetryTest
    @EnabledIf("hasOlderMinorChannel")
    void testDowngradeChannelSwitchIsRejected() throws Exception {
        setUp();

        var headVersion = catalog.getChannelHeadVersion(rollingChannel());
        var olderMinorChannel = catalog.getOlderMinorChannel(projectVersion(), rollingChannel());
        var olderHeadVersion = catalog.getChannelHeadVersion(olderMinorChannel);

        log.info("Testing downgrade: install current version on {}, then switch to {} "
                + "without changing the version constraint", rollingChannel(), olderMinorChannel);

        deployClusterExtension(rollingChannel(), headVersion);
        waitForOperatorVersion(headVersion);
        log.info("Operator {} deployed on {} channel", headVersion, rollingChannel());

        patchClusterExtensionSource(olderMinorChannel, headVersion);
        log.info("Switched ClusterExtension to {} channel (version constraint unchanged: {}), "
                + "verifying no downgrade...", olderMinorChannel, headVersion);

        await().atMost(MEDIUM_DURATION).during(Duration.ofSeconds(30)).ignoreExceptions()
                .untilAsserted(() -> {
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
                });

        log.info("Downgrade rejection confirmed: operator stayed at {}", headVersion);
    }

    // ---- Infrastructure methods ----

    private static String deploymentName(Semver version) {
        return OPERATOR_DEPLOYMENT_PREFIX + version;
    }

    /**
     * Deploys the catalog, RBAC and a {@code ClusterExtension} on {@code channel} with no
     * {@code spec.source.catalog.version}, leaving the version for OLM v1 to resolve (expected: the
     * channel head).
     */
    private void deployClusterExtensionChannelOnly(String channel) throws Exception {
        deployClusterExtension(channel, null);
    }

    /**
     * Deploys the catalog, RBAC and a {@code ClusterExtension} on {@code channel}. When {@code version}
     * is non-null it is pinned as {@code spec.source.catalog.version}; when null, the version constraint
     * line is dropped so OLM v1 resolves the version itself.
     */
    private void deployClusterExtension(String channel, Semver version) throws Exception {
        try {
            createResource(client, namespace, "olmv1/cluster-catalog.yaml");
            waitForClusterCatalogServing(client, namespace, CATALOG_NAME);
            createResource(client, namespace, "olmv1/service-account.yaml");
            createResource(client, namespace, "olmv1/cluster-role.yaml");
            createResource(client, namespace, "olmv1/cluster-role-binding.yaml");

            var raw = loadRawResource("olmv1/cluster-extension-upgrade.yaml");
            Map<String, String> extraVars;
            if (version == null) {
                // Drop only the version-constraint line so OLM resolves spec.source.catalog.version
                // itself. The ${PLACEHOLDER_VERSION} label line is left untouched.
                raw = raw.replaceAll("(?m)^.*\\$\\{PLACEHOLDER_UPGRADE_VERSION}.*\\R?", "");
                extraVars = Map.of("${PLACEHOLDER_UPGRADE_CHANNEL}", channel);
            } else {
                extraVars = Map.of(
                        "${PLACEHOLDER_UPGRADE_CHANNEL}", channel,
                        "${PLACEHOLDER_UPGRADE_VERSION}", version.toString());
            }
            client.resource(replaceVars(raw, namespace, extraVars)).create();
        } catch (Exception e) {
            log.error("OLM v1 catalog/ClusterExtension setup failed, dumping cluster diagnostics", e);
            ClusterDiagnostics.dump(client, namespace, true);
            throw e;
        }
    }

    /**
     * Waits until a registry operator deployment ({@code apicurio-registry-operator-v*}) is ready and
     * returns the version OLM actually resolved, parsed from that deployment's name.
     */
    private Semver waitForResolvedOperatorVersion() {
        var resolved = new AtomicReference<Semver>();
        await().atMost(UPGRADE_TIMEOUT).ignoreExceptions().untilAsserted(() -> {
            var ready = client.apps().deployments().inNamespace(namespace).list().getItems().stream()
                    .filter(d -> d.getMetadata().getName().startsWith(OPERATOR_DEPLOYMENT_PREFIX))
                    .filter(d -> Integer.valueOf(1).equals(d.getStatus().getReadyReplicas()))
                    .findFirst();
            assertThat(ready)
                    .as("A registry operator deployment (%s*) should become ready",
                            OPERATOR_DEPLOYMENT_PREFIX)
                    .isPresent();
            resolved.set(CatalogInfo.parseVersion(ready.get().getMetadata().getName()
                    .substring(OPERATOR_DEPLOYMENT_PREFIX.length())));
        });
        return resolved.get();
    }

    private void waitForOperatorVersion(Semver version) {
        var name = deploymentName(version);
        await().atMost(UPGRADE_TIMEOUT).ignoreExceptions().untilAsserted(() -> {
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
        await().atMost(UPGRADE_TIMEOUT).ignoreExceptions().untilAsserted(() -> {
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
    private void patchClusterExtensionSource(String channel, Semver version) {
        var ce = client.genericKubernetesResources("olm.operatorframework.io/v1", "ClusterExtension")
                .inNamespace(namespace)
                .withName(CLUSTER_EXTENSION_NAME)
                .get();
        assertThat(ce).as("ClusterExtension should exist").isNotNull();

        var spec = (Map<String, Object>) ce.getAdditionalProperties().get("spec");
        var source = (Map<String, Object>) spec.get("source");
        var catalogSource = (Map<String, Object>) source.get("catalog");
        catalogSource.put("channels", List.of(channel));
        catalogSource.put("version", version.toString());
        client.genericKubernetesResources("olm.operatorframework.io/v1", "ClusterExtension")
                .inNamespace(namespace)
                .resource(ce)
                .update();
        log.info("Patched ClusterExtension catalog source: channel={}, version={}", channel, version);
    }
}
