package io.apicurio.registry.systemtest.framework;

import io.apicurio.registry.systemtest.registryinfra.resources.ResourceKind;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.apps.Deployment;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

public class OperatorUtils {
    public static String getApicurioRegistryOperatorNamespace() {
        return System.getenv().getOrDefault(Constants.APICURIO_REGISTRY_OPERATOR_NAMESPACE_ENV_VARIABLE, Constants.APICURIO_REGISTRY_OPERATOR_NAMESPACE_DEFAULT_VALUE);
    }

    public static String getStrimziOperatorNamespace() {
        return System.getenv().getOrDefault(Constants.STRIMZI_CLUSTER_OPERATOR_NAMESPACE_ENV_VARIABLE, Constants.STRIMZI_CLUSTER_OPERATOR_NAMESPACE_DEFAULT_VALUE);
    }

    public static Deployment findDeployment(List<HasMetadata> resourceList) {
        for (HasMetadata r : resourceList) {
            if (r.getKind().equals(ResourceKind.DEPLOYMENT)) {
                return (Deployment) r;
            }
        }

        return null;
    }

    public static void downloadFile(String url, String path) throws Exception {
        try (InputStream inputStream = (new URL(url)).openStream()) {
            Files.copy(inputStream, Paths.get(path), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
