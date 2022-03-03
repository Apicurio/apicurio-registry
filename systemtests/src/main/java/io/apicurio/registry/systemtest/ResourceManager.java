package io.apicurio.registry.systemtest;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.apicurio.registry.operator.api.model.ApicurioRegistry;
import io.apicurio.registry.operator.api.model.ApicurioRegistryBuilder;
import io.apicurio.registry.operator.api.model.ApicurioRegistryList;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.internal.SerializationUtils;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.openshift.client.OpenShiftConfig;

public class ResourceManager {
    private static ResourceManager instance;

    private static Config config;

    private static OpenShiftClient ocClient;

    private static MixedOperation<ApicurioRegistry, ApicurioRegistryList, Resource<ApicurioRegistry>> resourceClient;

    private ResourceManager() throws JsonProcessingException {
        config = Config.autoConfigure(System.getenv().getOrDefault("TEST_CLUSTER_CONTEXT", null));

        ocClient = new DefaultOpenShiftClient(new OpenShiftConfig(config));

        resourceClient = ocClient.resources(ApicurioRegistry.class, ApicurioRegistryList.class);
    }

    public static synchronized ResourceManager getInstance() {
        if (instance == null) {
            try {
                instance = new ResourceManager();
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
        }
        return instance;
    }

    public final void createResource(ApicurioRegistry ar) {
        resourceClient.inNamespace("apicurio-test").createOrReplace(ar);
    }

    public final void deleteResource(ApicurioRegistry ar) {
        resourceClient.inNamespace("apicurio-test").delete(ar);
    }
}
