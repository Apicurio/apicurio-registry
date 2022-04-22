package io.apicurio.registry.systemtest.framework;

import io.apicurio.registry.systemtest.platform.Kubernetes;
import io.apicurio.registry.systemtest.registryinfra.resources.ResourceKind;
import io.apicurio.registry.systemtest.time.TimeoutBudget;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import org.slf4j.Logger;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;

public class OperatorUtils {
    private static final Logger operatorUtilsLogger = LoggerUtils.getLogger();
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

    public static void downloadFile(String source, String destination) throws Exception {
        try (InputStream inputStream = (new URL(source)).openStream()) {
            Files.copy(inputStream, Paths.get(destination), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static boolean waitNamespaceReady(String namespaceName) {
        return waitNamespaceReady(namespaceName, TimeoutBudget.ofDuration(Duration.ofMinutes(1)));
    }

    public static boolean waitNamespaceReady(String namespaceName, TimeoutBudget timeoutBudget) {
        while (!timeoutBudget.timeoutExpired()) {
            if (Kubernetes.getClient().namespaces().withName(namespaceName).get().getStatus().getPhase().equals("Active")) {
                return true;
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();

                return false;
            }
        }

        boolean pass = Kubernetes.getClient().namespaces().withName(namespaceName).get().getStatus().getPhase().equals("Active");

        if (!pass) {
            operatorUtilsLogger.info("Namespace {} failed readiness check.", namespaceName);
        }

        return pass;
    }

    public static boolean waitNamespaceRemoved(String namespaceName) {
        return waitNamespaceRemoved(namespaceName, TimeoutBudget.ofDuration(Duration.ofMinutes(5)));
    }

    public static boolean waitNamespaceRemoved(String namespaceName, TimeoutBudget timeoutBudget) {
        while (!timeoutBudget.timeoutExpired()) {
            if (Kubernetes.getClient().namespaces().withName(namespaceName).get() == null) {
                return true;
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();

                return false;
            }
        }

        boolean pass = Kubernetes.getClient().namespaces().withName(namespaceName).get() == null;

        if (!pass) {
            operatorUtilsLogger.info("Namespace {} failed removal check.", namespaceName);
        }

        return pass;
    }

}
