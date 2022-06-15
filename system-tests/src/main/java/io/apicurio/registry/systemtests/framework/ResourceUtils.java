package io.apicurio.registry.systemtests.framework;

import io.apicurio.registry.systemtests.platform.Kubernetes;
import io.apicurio.registry.systemtests.time.TimeoutBudget;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.api.model.rbac.Subject;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.List;

public class ResourceUtils {
    private static final Logger LOGGER = LoggerUtils.getLogger();

    public static boolean waitStatefulSetReady(String namespace, String name) {
        return waitStatefulSetReady(namespace, name, TimeoutBudget.ofDuration(Duration.ofMinutes(5)));
    }

    public static boolean waitStatefulSetReady(String namespace, String name, TimeoutBudget timeoutBudget) {
        while (!timeoutBudget.timeoutExpired()) {
            if (Kubernetes.isStatefulSetReady(namespace, name)) {
                return true;
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();

                return false;
            }
        }

        if (!Kubernetes.isStatefulSetReady(namespace, name)) {
            LOGGER.error("StatefulSet with name {} in namespace {} failed readiness check.", name, namespace);

            return false;
        }

        return true;
    }

    public static boolean waitPackageManifestExists(String catalog, String name) {
        return waitPackageManifestExists(catalog, name, TimeoutBudget.ofDuration(Duration.ofMinutes(5)));
    }

    public static boolean waitPackageManifestExists(String catalog, String name, TimeoutBudget timeoutBudget) {
        while (!timeoutBudget.timeoutExpired()) {
            if (Kubernetes.getPackageManifest(catalog, name) != null) {
                return true;
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();

                return false;
            }
        }

        if (Kubernetes.getPackageManifest(catalog, name) == null) {
            LOGGER.error("PackageManifest with name {} in catalog {} failed existence check.", name, catalog);

            return false;
        }

        return true;
    }

    public static void updateRoleBindingNamespace(List<HasMetadata> resources, String namespace) {
        // Go through all loaded operator resources
        for (HasMetadata resource : resources) {
            // If resource is RoleBinding
            if (resource.getKind().equals("RoleBinding")) {
                // Iterate over all subjects in this RoleBinding
                for (Subject s: ((RoleBinding) resource).getSubjects()) {
                    // Change namespace of subject to operator namespace
                    s.setNamespace(namespace);
                }
                // If resource is ClusterRoleBinding
            } else if (resource.getKind().equals("ClusterRoleBinding")) {
                // Iterate over all subjects in this ClusterRoleBinding
                for (Subject s : ((ClusterRoleBinding) resource).getSubjects()) {
                    // Change namespace of subject to operator namespace
                    s.setNamespace(namespace);
                }
            }
        }
    }
}
