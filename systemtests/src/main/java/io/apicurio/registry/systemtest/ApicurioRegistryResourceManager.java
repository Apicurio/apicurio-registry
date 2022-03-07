package io.apicurio.registry.systemtest;

import io.apicurio.registry.operator.api.model.ApicurioRegistry;
import io.apicurio.registry.operator.api.model.ApicurioRegistryList;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.openshift.client.OpenShiftConfig;

import java.util.HashMap;

public class ApicurioRegistryResourceManager extends AbstractResourceManager<ApicurioRegistry, ApicurioRegistryList> {
    protected static ApicurioRegistryResourceManager instance;

    protected Config config;

    protected OpenShiftClient ocClient;

    @Override
    protected Class<ApicurioRegistry> getResourceClass() {
        return ApicurioRegistry.class;
    }

    @Override
    protected Class<ApicurioRegistryList> getResourceListClass() {
        return ApicurioRegistryList.class;
    }

    private ApicurioRegistryResourceManager() {
        createdResources = new HashMap<>();

        config = Config.autoConfigure(System.getenv().getOrDefault("TEST_CLUSTER_CONTEXT", null));

        ocClient = new DefaultOpenShiftClient(new OpenShiftConfig(config));

        resourceClient = ocClient.resources(getResourceClass(), getResourceListClass());
    }

    public static synchronized ApicurioRegistryResourceManager getInstance() {
        if (instance == null) {
            instance = new ApicurioRegistryResourceManager();
        }
        return instance;
    }
}
