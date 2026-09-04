package io.apicurio.registry.operator.feat;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.GenericKubernetesResourceBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class ConsolePluginManagerOwnershipTest {

    @Test
    void testBelongsToMatchesOwnBackendService() {
        var registry = registry("team-a", "example-apicurioregistry");
        var plugin = pluginFor("example-apicurioregistry-console-plugin-service", "team-a");

        assertThat(ConsolePluginManager.belongsTo(plugin, registry)).isTrue();
    }

    @Test
    void testBelongsToRejectsUnrelatedCRWithCollidingLegacyName() {
        // Regression test for the corner case raised on
        // https://github.com/Apicurio/apicurio-registry/pull/9642 : getLegacyPluginName("team-a") and
        // getPluginName(<CR named "team-a" in namespace "team">) both produce "team-a-console-plugin", so
        // a name-only lookup during the legacy-cleanup migration could otherwise find and delete an
        // unrelated CR's already-migrated, namespace-scoped ConsolePlugin.
        var unrelatedCR = registry("team", "a");
        var collidingPlugin = pluginFor("a-console-plugin-service", "team");

        var thisCR = registry("some-namespace", "team-a");

        assertThat(ConsolePluginManager.belongsTo(collidingPlugin, thisCR)).isFalse();
        assertThat(ConsolePluginManager.belongsTo(collidingPlugin, unrelatedCR)).isTrue();
    }

    @Test
    void testBelongsToRejectsPluginWithNoBackendService() {
        var registry = registry("team-a", "example-apicurioregistry");
        var plugin = new GenericKubernetesResourceBuilder()
                .withNewMetadata().withName("example-apicurioregistry-console-plugin").endMetadata()
                .build();

        assertThat(ConsolePluginManager.belongsTo(plugin, registry)).isFalse();
    }

    private static ApicurioRegistry3 registry(String namespace, String name) {
        var registry = new ApicurioRegistry3();
        registry.setMetadata(new ObjectMetaBuilder().withNamespace(namespace).withName(name).build());
        return registry;
    }

    private static GenericKubernetesResource pluginFor(String serviceName, String serviceNamespace) {
        var plugin = new GenericKubernetesResourceBuilder()
                .withNewMetadata().withName(serviceName + "-plugin").endMetadata()
                .build();
        plugin.setAdditionalProperties(Map.of(
                "spec", Map.of(
                        "backend", Map.of(
                                "service", Map.of(
                                        "name", serviceName,
                                        "namespace", serviceNamespace
                                )
                        ),
                        "proxy", List.of()
                )
        ));
        return plugin;
    }
}
