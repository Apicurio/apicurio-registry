package io.apicurio.registry.operator.unit;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.feat.ConsolePluginManager;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ConsolePluginManagerTest {

    @Test
    void testPluginNameIncludesNamespace() {
        var registry = registry("team-a", "example-apicurioregistry");

        assertThat(ConsolePluginManager.getPluginName(registry))
                .isEqualTo("team-a-example-apicurioregistry-console-plugin");
    }

    @Test
    void testPluginNameDiffersAcrossNamespacesForSameCRName() {
        // Regression test for https://github.com/Apicurio/apicurio-registry/issues/9640 :
        // ConsolePlugin is a cluster-scoped resource, so two ApicurioRegistry3 CRs with the same
        // name in different namespaces must not compute the same plugin name, or the operator
        // would silently overwrite/delete one CR's console integration while reconciling the other.
        var teamA = registry("team-a", "example-apicurioregistry");
        var teamB = registry("team-b", "example-apicurioregistry");

        assertThat(ConsolePluginManager.getPluginName(teamA))
                .isNotEqualTo(ConsolePluginManager.getPluginName(teamB));
    }

    @Test
    void testPluginNameDiffersAcrossCRNamesInSameNamespace() {
        var first = registry("team-a", "registry-one");
        var second = registry("team-a", "registry-two");

        assertThat(ConsolePluginManager.getPluginName(first))
                .isNotEqualTo(ConsolePluginManager.getPluginName(second));
    }

    private static ApicurioRegistry3 registry(String namespace, String name) {
        var registry = new ApicurioRegistry3();
        registry.setMetadata(new ObjectMetaBuilder().withNamespace(namespace).withName(name).build());
        return registry;
    }
}
