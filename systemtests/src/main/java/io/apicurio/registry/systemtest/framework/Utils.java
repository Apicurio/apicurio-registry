package io.apicurio.registry.systemtest.framework;

import io.apicurio.registry.systemtest.executor.Exec;
import io.apicurio.registry.systemtest.platform.Kubernetes;
import io.apicurio.registry.systemtest.registryinfra.ResourceManager;
import io.apicurio.registry.systemtest.registryinfra.resources.RouteResourceType;
import io.apicurio.registry.systemtest.registryinfra.resources.ServiceResourceType;
import io.apicurio.registry.systemtest.time.TimeoutBudget;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.openshift.client.OpenShiftClient;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;

import java.nio.file.Paths;
import java.time.Duration;

public class Utils {
    private static final Logger LOGGER = LoggerUtils.getLogger();

    public static String getTestsuiteDirectory() {
        return System.getenv().get(Constants.TESTSUITE_DIRECTORY_ENV_VAR);
    }

    public static boolean waitStatefulSetReady(String namespace, String name) {
        return waitStatefulSetReady(namespace, name, TimeoutBudget.ofDuration(Duration.ofMinutes(5)));
    }

    public static boolean waitStatefulSetReady(String namespace, String name, TimeoutBudget timeoutBudget) {
        boolean pass = false;
        StatefulSet statefulSet = Kubernetes.getClient().apps().statefulSets().inNamespace(namespace).withName(name).get();

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

        statefulSet = Kubernetes.getClient().apps().statefulSets().inNamespace(namespace).withName(name).get();

        if(statefulSet != null && statefulSet.getStatus() != null) {
            pass = statefulSet.getStatus().getReadyReplicas() > 0;
        }

        if (!pass) {
            LOGGER.info("StatefulSet with name {} in namespace {} failed readiness check.", name, namespace);
        }

        return pass;
    }

    public static void deployKeycloak(ExtensionContext testContext, String namespace) {
        // Deploy Keycloak server
        Exec.executeAndCheck("oc", "apply", "-n", namespace, "-f", Paths.get(Utils.getTestsuiteDirectory(), "kubefiles", "keycloak", "keycloak.yaml").toString());

        // Wait for Keycloak server to be ready
        waitStatefulSetReady(namespace, "keycloak");

        // Create Keycloak HTTP Service and do not wait for its readiness
        ResourceManager.getInstance().createResource(testContext, false, ServiceResourceType.getDefaultKeycloakHttp(namespace));

        // Create Keycloak Route and wait for its readiness
        ResourceManager.getInstance().createResource(testContext, true, RouteResourceType.getDefaultKeycloak(namespace));

        // Log Keycloak URL
        LOGGER.info("Keycloak URL: {}", getDefaultKeycloakURL(namespace));

        // Create Keycloak Realm
        Exec.executeAndCheck("oc", "apply", "-n", namespace, "-f", Paths.get(Utils.getTestsuiteDirectory(), "kubefiles", "keycloak", "keycloak-realm.yaml").toString());
    }

    public static void removeKeycloak(String namespace) {
        Exec.executeAndCheck("oc", "delete", "-n", namespace, "-f", Paths.get(Utils.getTestsuiteDirectory(), "kubefiles", "keycloak", "keycloak-realm.yaml").toString());

        Exec.executeAndCheck("oc", "delete", "-n", namespace, "-f", Paths.get(Utils.getTestsuiteDirectory(), "kubefiles", "keycloak", "keycloak.yaml").toString());
    }

    public static String getKeycloakURL(String namespace, String name) {
        return "http://" + ((OpenShiftClient) Kubernetes.getClient()).routes().inNamespace(namespace).withName(name).get().getStatus().getIngress().get(0).getHost();
    }

    public static String getDefaultKeycloakURL(String namespace) {
        return getKeycloakURL(namespace, Constants.KEYCLOAK_HTTP_SERVICE_NAME);
    }
}
