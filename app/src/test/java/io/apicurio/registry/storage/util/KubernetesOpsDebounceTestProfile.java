package io.apicurio.registry.storage.util;

import io.apicurio.registry.storage.impl.kubernetesops.KubernetesTestResourceManager;

import java.util.List;
import java.util.Map;

/**
 * {@link KubernetesOpsTestProfile} with debouncing on: a short quiet period and a maximum wait far
 * longer than any test waits, so a change is only loaded if the quiet period is honoured.
 */
public class KubernetesOpsDebounceTestProfile extends KubernetesOpsTestProfile {

    @Override
    public List<TestResourceEntry> testResources() {
        return List.of(new TestResourceEntry(KubernetesTestResourceManager.class,
                Map.of("quiet-period", "PT2S", "max-wait-period", "PT10M")));
    }
}
