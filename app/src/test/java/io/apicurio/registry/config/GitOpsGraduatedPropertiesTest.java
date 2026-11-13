package io.apicurio.registry.config;

import io.apicurio.common.apps.config.ExperimentalConfigPropertyDef;
import io.apicurio.common.apps.config.ExperimentalConfigPropertyList;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GitOps storage is supported since 3.4.0, so its properties and the shared polling storage properties
 * are no longer experimental: with the experimental features gate off (the default), they are not in the
 * set discovered at build time, and {@link ExperimentalFeaturesConfig#canEnable(String)} allows them.
 * KubernetesOps stays experimental.
 */
@QuarkusTest
class GitOpsGraduatedPropertiesTest {

    private static final List<String> GRADUATED = List.of(
            "apicurio.gitops.workspace",
            "apicurio.gitops.repo.dir",
            "apicurio.gitops.repo.branch",
            "apicurio.gitops.validate.enabled",
            "apicurio.gitops.validate.task.ttl.seconds",
            "apicurio.gitops.validate.max-tasks",
            "apicurio.gitops.validate.fetch.timeout.seconds",
            "apicurio.polling-storage.id",
            "apicurio.polling-storage.deterministic-ids-enabled",
            "apicurio.polling-storage.poll-period",
            "apicurio.polling-storage.debounce.quiet-period",
            "apicurio.polling-storage.debounce.max-wait-period",
            "apicurio.polling-storage.file-suffixes",
            "apicurio.polling-storage.require-registry-config",
            "apicurio.polling-storage.source-label-key");

    private static final List<String> STILL_EXPERIMENTAL = List.of(
            "apicurio.kubernetesops.namespace",
            "apicurio.kubernetesops.label.registry-id",
            "apicurio.kubernetesops.watch.enabled",
            "apicurio.kubernetesops.watch.reconnect-delay");

    @Inject
    ExperimentalConfigPropertyList experimentalProperties;

    @Inject
    ExperimentalFeaturesConfig experimentalFeatures;

    private Set<String> experimentalNames() {
        return experimentalProperties.getExperimentalConfigProperties().stream()
                .map(ExperimentalConfigPropertyDef::getName).collect(Collectors.toSet());
    }

    @Test
    void graduatedPropertiesAreNotExperimental() {
        Set<String> experimental = experimentalNames();
        for (String name : GRADUATED) {
            assertFalse(experimental.contains(name), name + " is still in the experimental set");
            assertTrue(experimentalFeatures.canEnable(name), name + " should be allowed with the gate off");
        }
    }

    @Test
    void kubernetesOpsPropertiesStayExperimental() {
        // Also shows that the set is discovered in this test, so the check above isn't vacuous
        Set<String> experimental = experimentalNames();
        for (String name : STILL_EXPERIMENTAL) {
            assertTrue(experimental.contains(name), name + " is missing from the experimental set");
            assertFalse(experimentalFeatures.canEnable(name), name + " should be refused with the gate off");
        }
    }
}
