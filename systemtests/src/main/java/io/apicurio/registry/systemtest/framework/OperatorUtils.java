package io.apicurio.registry.systemtest.framework;

import io.apicurio.registry.systemtest.platform.Kubernetes;
import io.apicurio.registry.systemtest.registryinfra.ResourceManager;
import io.apicurio.registry.systemtest.registryinfra.resources.ResourceKind;
import io.apicurio.registry.systemtest.time.TimeoutBudget;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.openshift.api.model.operatorhub.lifecyclemanager.v1.PackageChannel;
import io.fabric8.openshift.api.model.operatorhub.lifecyclemanager.v1.PackageManifest;
import io.fabric8.openshift.api.model.operatorhub.v1.OperatorGroup;
import io.fabric8.openshift.api.model.operatorhub.v1.OperatorGroupBuilder;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.CatalogSource;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.Subscription;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.SubscriptionSpec;
import io.fabric8.openshift.client.OpenShiftClient;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.MessageFormat;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

public class OperatorUtils {
    private static final Logger LOGGER = LoggerUtils.getLogger();

    public static List<String> listFilesInDirectory(Path directory) throws IOException {
        return Files.list(directory)
                .filter(file -> !Files.isDirectory(file))
                .map(Path::getFileName)
                .map(Path::toString)
                .collect(Collectors.toList());
    }

    public static Deployment findDeploymentInResourceList(List<HasMetadata> resourceList) {
        for (HasMetadata r : resourceList) {
            if (r.getKind().equals(ResourceKind.DEPLOYMENT)) {
                return (Deployment) r;
            }
        }

        return null;
    }

    public static void downloadFile(String source, Path destination) throws Exception {
        LOGGER.info("Downloading file " + source + " to " + destination + "...");

        try (InputStream inputStream = (new URL(source)).openStream()) {
            Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static boolean waitNamespaceReady(String name) {
        return waitNamespaceReady(name, TimeoutBudget.ofDuration(Duration.ofMinutes(1)));
    }

    public static boolean waitNamespaceReady(String name, TimeoutBudget timeoutBudget) {
        while (!timeoutBudget.timeoutExpired()) {
            if (Kubernetes.isNamespaceActive(name)) {
                return true;
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();

                return false;
            }
        }

        if (!Kubernetes.isNamespaceActive(name)) {
            LOGGER.error("Namespace {} failed readiness check.", name);

            return false;
        }

        return true;
    }

    public static boolean waitNamespaceRemoved(String name) {
        return waitNamespaceRemoved(name, TimeoutBudget.ofDuration(Duration.ofMinutes(5)));
    }

    public static boolean waitNamespaceRemoved(String name, TimeoutBudget timeoutBudget) {
        while (!timeoutBudget.timeoutExpired()) {
            if (Kubernetes.getNamespace(name) == null) {
                return true;
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();

                return false;
            }
        }

        if (Kubernetes.getNamespace(name) != null) {
            LOGGER.error("Namespace {} failed removal check.", name);

            return false;
        }

        return true;
    }

    public static boolean waitPodsExist(String namespace, String labelKey, String labelValue, TimeoutBudget timeout) {
        while (!timeout.timeoutExpired()) {
            if (Kubernetes.getPods(namespace, labelKey, labelValue).getItems().size() > 0) {
                return true;
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();

                return false;
            }
        }

        if (Kubernetes.getPods(namespace, labelKey, labelValue).getItems().size() == 0) {
            LOGGER.error(
                    "Pod(s) of catalog source in namespace {} with label {}={} failed creation check.",
                    namespace, labelKey, labelValue
            );

            return false;
        }

        return true;
    }

    public static boolean waitPodsExist(String namespace, String labelKey, String labelValue) {
        return waitPodsExist(namespace, labelKey, labelValue, TimeoutBudget.ofDuration(Duration.ofMinutes(3)));
    }

    private static boolean collectPodsReadiness(PodList podList) {
        if (podList.getItems().size() > 0) {
            boolean allPodsReady = true;

            for (Pod p : podList.getItems()) {
                boolean podReady = false;

                if (
                        p.getStatus() != null
                        && p.getStatus().getContainerStatuses() != null
                        && p.getStatus().getContainerStatuses().size() > 0
                ) {
                    podReady = p.getStatus().getContainerStatuses().get(0).getReady();
                }

                allPodsReady = allPodsReady && podReady;
            }

            return allPodsReady;
        }

        return false;
    }

    public static boolean waitPodsReady(String namespace, String labelKey, String labelValue, TimeoutBudget timeout) {
        while (!timeout.timeoutExpired()) {
            if (collectPodsReadiness(Kubernetes.getPods(namespace, labelKey, labelValue))) {
                return true;
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();

                return false;
            }
        }

        if (!collectPodsReadiness(Kubernetes.getPods(namespace, labelKey, labelValue))) {
            LOGGER.error(
                    "Pod(s) of catalog source in namespace {} with label {}={} failed readiness check.",
                    namespace, labelKey, labelValue
            );

            return false;
        }

        return true;
    }

    public static boolean waitPodsReady(String namespace, String labelKey, String labelValue) {
        return waitPodsReady(namespace, labelKey, labelValue, TimeoutBudget.ofDuration(Duration.ofMinutes(3)));
    }

    public static boolean waitCatalogSourceExists(String namespace, String name, TimeoutBudget timeout) {
        while (!timeout.timeoutExpired()) {
            if (Kubernetes.getCatalogSource(namespace, name) != null) {
                return true;
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();

                return false;
            }
        }

        if (Kubernetes.getCatalogSource(namespace, name) == null) {
            LOGGER.error("Catalog source in namespace {} with name {} failed creation check.", namespace, name);

            return false;
        }

        return true;
    }

    public static boolean waitCatalogSourceExists(String namespace, String name) {
        return waitCatalogSourceExists(namespace, name, TimeoutBudget.ofDuration(Duration.ofMinutes(3)));
    }

    private static boolean isCatalogSourceReady(CatalogSource catalogSource) {
        if (catalogSource == null || catalogSource.getStatus() == null) {
            return  false;
        }

        if (catalogSource.getStatus().getConnectionState().getLastObservedState().equals("READY")) {
            return true;
        }

        return false;
    }

    public static boolean waitCatalogSourceReady(String namespace, String name, TimeoutBudget timeout) {
        while (!timeout.timeoutExpired()) {
            if (isCatalogSourceReady(Kubernetes.getCatalogSource(namespace, name))) {
                return true;
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();

                return false;
            }
        }

        if (!isCatalogSourceReady(Kubernetes.getCatalogSource(namespace, name))) {
            LOGGER.error("Catalog source in namespace {} with name {} failed readiness check.", namespace, name);

            return false;
        }

        return true;
    }

    public static boolean waitCatalogSourceReady(String namespace, String name) {
        return waitCatalogSourceReady(namespace, name, TimeoutBudget.ofDuration(Duration.ofMinutes(5)));
    }

    public static boolean namespaceHasAnyOperatorGroup(String name) {
        int namespaceOperatorGroupsCount = ((OpenShiftClient) Kubernetes.getClient())
                .operatorHub()
                .operatorGroups()
                .inNamespace(name)
                .list()
                .getItems()
                .size();

        return namespaceOperatorGroupsCount > 0;
    }

    public static OperatorGroup createOperatorGroup(ExtensionContext testContext, String namespace) {
        String name = namespace + "-operator-group";

        LOGGER.info("Creating operator group {} in namespace {} targeting namespace {}...", name, namespace, namespace);

        OperatorGroup operatorGroup = new OperatorGroupBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withNamespace(namespace)
                .endMetadata()
                .withNewSpec()
                    .withTargetNamespaces(namespace)
                .endSpec()
                .build();

        ResourceManager.getInstance().createResource(testContext, true, operatorGroup);

        return operatorGroup;
    }

    public static void deleteOperatorGroup(OperatorGroup operatorGroup) {
        String name = operatorGroup.getMetadata().getName();
        String namespace = operatorGroup.getMetadata().getNamespace();
        List<String> targetNamespaces = operatorGroup.getSpec().getTargetNamespaces();
        String info = MessageFormat.format(
                "{0} in namespace {1} targeting namespaces {2}", name, namespace, targetNamespaces
        );

        if (Kubernetes.getOperatorGroup(namespace, name) == null) {
            LOGGER.info("Operator group {} already removed.", info);
        } else {
            LOGGER.info("Removing operator group {}...", info);

            Kubernetes.deleteOperatorGroup(namespace, name);

            // TODO: Wait for removal?
        }
    }

    public static void deleteSubscription(Subscription subscription) {
        String name = subscription.getMetadata().getName();
        String namespace = subscription.getMetadata().getNamespace();
        SubscriptionSpec spec = subscription.getSpec();
        String startingCSV = spec.getStartingCSV();

        String info = MessageFormat.format(
                "{0} in namespace {1}: packageName={2}, catalogSourceName={3}, catalogSourceNamespace={4}, " +
                        "startingCSV={5}, channel={6}, installPlanApproval={7}",
                name, namespace, spec.getName(), spec.getSource(), spec.getSourceNamespace(),
                startingCSV, spec.getChannel(), spec.getInstallPlanApproval()
        );

        if (Kubernetes.getSubscription(namespace, name) == null) {
            LOGGER.info("Subscription {} already removed.", info);
        } else {
            LOGGER.info("Removing subscription {}...", info);

            Kubernetes.deleteSubscription(namespace, name);

            if (!startingCSV.equals("")) {
                LOGGER.info("Removing ClusterServiceVersion {} in namespace {}...", startingCSV, namespace);

                Kubernetes.deleteClusterServiceVersion(namespace, startingCSV);

                if (Kubernetes.getClusterServiceVersion(namespace, startingCSV) == null) {
                    LOGGER.info("ClusterServiceVersion {} in namespace {} removed.", startingCSV, namespace);
                }
            }
        }
    }

    public static String getChannelsCurrentCSV(PackageManifest packageManifest, String channelName) {
        for (PackageChannel packageChannel : packageManifest.getStatus().getChannels()) {
            if (packageChannel.getName().equals(channelName)) {
                return packageChannel.getCurrentCSV();
            }
        }

        return null;
    }
}
