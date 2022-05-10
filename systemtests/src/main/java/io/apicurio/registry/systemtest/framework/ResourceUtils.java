package io.apicurio.registry.systemtest.framework;

import io.apicurio.registry.systemtest.platform.Kubernetes;
import io.apicurio.registry.systemtest.time.TimeoutBudget;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
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
        StatefulSet statefulSet = Kubernetes.getStatefulSet(namespace, name);

        while (!timeoutBudget.timeoutExpired()) {
            if(statefulSet == null || statefulSet.getStatus() == null) {
                return false;
            }

            if (statefulSet.getStatus().getReadyReplicas() > 0) {
                return true;
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();

                return false;
            }
        }

        statefulSet = Kubernetes.getStatefulSet(namespace, name);

        if(statefulSet == null || statefulSet.getStatus() == null || statefulSet.getStatus().getReadyReplicas() <= 0) {
            LOGGER.error("StatefulSet with name {} in namespace {} failed readiness check.", name, namespace);

            return false;
        }

        return true;
    }

    public static void updateRoleBindingNamespace(List<HasMetadata> resources, String namespace) {
        // Go through all loaded operator resources
        for(HasMetadata resource : resources) {
            // If resource is RoleBinding
            if(resource.getKind().equals("RoleBinding")) {
                // Iterate over all subjects in this RoleBinding
                for(Subject s: ((RoleBinding) resource).getSubjects()) {
                    // Change namespace of subject to operator namespace
                    s.setNamespace(namespace);
                }
                // If resource is ClusterRoleBinding
            } else if(resource.getKind().equals("ClusterRoleBinding")) {
                // Iterate over all subjects in this ClusterRoleBinding
                for(Subject s : ((ClusterRoleBinding) resource).getSubjects()) {
                    // Change namespace of subject to operator namespace
                    s.setNamespace(namespace);
                }
            }
        }
    }

    public static Namespace buildNamespace(String name) {
        return new NamespaceBuilder()
                .editOrNewMetadata()
                    .withName(name)
                .endMetadata()
                .build();
    }
}
