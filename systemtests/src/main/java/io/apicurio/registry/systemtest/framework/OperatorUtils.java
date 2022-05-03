package io.apicurio.registry.systemtest.framework;

import io.apicurio.registry.systemtest.platform.Kubernetes;
import io.apicurio.registry.systemtest.registryinfra.resources.ResourceKind;
import io.apicurio.registry.systemtest.time.TimeoutBudget;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.openshift.api.model.operatorhub.v1.OperatorGroup;
import io.fabric8.openshift.api.model.operatorhub.v1.OperatorGroupBuilder;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.Subscription;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.SubscriptionBuilder;
import io.fabric8.openshift.client.OpenShiftClient;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

public class OperatorUtils {
    private static final Logger LOGGER = LoggerUtils.getLogger();

    public static List<String> listFiles(Path directory) throws IOException {
        return Files.list(directory)
                .filter(file -> !Files.isDirectory(file))
                .map(Path::getFileName)
                .map(Path::toString)
                .collect(Collectors.toList());
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
            LOGGER.info("Namespace {} failed readiness check.", namespaceName);
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
            LOGGER.info("Namespace {} failed removal check.", namespaceName);
        }

        return pass;
    }

    public static OperatorGroup createOperatorGroup(String operatorGroupName, String operatorNamespace) {
        LOGGER.info("Creating operator group {} in namespace {} targeting namespace {}...", operatorGroupName, operatorNamespace, operatorNamespace);

        OperatorGroup operatorGroup = new OperatorGroupBuilder()
                .withNewMetadata()
                .withName(operatorGroupName)
                .withNamespace(operatorNamespace)
                .endMetadata()
                .withNewSpec()
                .withTargetNamespaces(operatorNamespace)
                .endSpec()
                .build();

        ((OpenShiftClient) Kubernetes.getClient()).operatorHub().operatorGroups().inNamespace(operatorNamespace).create(operatorGroup);

        if(((OpenShiftClient) Kubernetes.getClient()).operatorHub().operatorGroups().inNamespace(operatorNamespace).withName(operatorGroupName).get() == null) {
            LOGGER.info("Operator group {} in namespace {} targeting namespace {} is not created.", operatorGroupName, operatorNamespace, operatorNamespace);

            return null;
        } else {
            LOGGER.info("Operator group {} in namespace {} targeting namespace {} created.", operatorGroupName, operatorNamespace, operatorNamespace);

            return operatorGroup;
        }
    }

    public static void deleteOperatorGroup(OperatorGroup operatorGroup) {
        if(operatorGroup != null) {
            if(((OpenShiftClient) Kubernetes.getClient()).operatorHub().operatorGroups().inNamespace(operatorGroup.getMetadata().getNamespace()).withName(operatorGroup.getMetadata().getName()).get() == null) {
                LOGGER.info("Operator group {} in namespace {} targeting namespace {} already removed.", operatorGroup.getMetadata().getName(), operatorGroup.getMetadata().getNamespace(), operatorGroup.getSpec().getTargetNamespaces());
            } else {
                LOGGER.info("Removing operator group {} in namespace {} targeting namespace {}...", operatorGroup.getMetadata().getName(), operatorGroup.getMetadata().getNamespace(), operatorGroup.getSpec().getTargetNamespaces());

                ((OpenShiftClient) Kubernetes.getClient()).operatorHub().operatorGroups().inNamespace(operatorGroup.getMetadata().getNamespace()).withName(operatorGroup.getMetadata().getName()).delete();

                // Wait for removal?
            }
        }
    }

    public static Subscription createSubscription(String subscriptionName, String operatorNamespace, String packageName, String catalogSourceName, String catalogSourceNamespaceName, String startingCSV, String channel, String installPlanApproval) {
        LOGGER.info("Creating subscription {} in namespace {}: packageName={}, catalogSourceName={}, catalogSourceNamespaceName={}, startingCSV={}, channel={}, installPlanApproval={}...",
                subscriptionName, operatorNamespace, packageName, catalogSourceName, catalogSourceNamespaceName, startingCSV, channel, installPlanApproval);

        Subscription subscription = new SubscriptionBuilder()
                .withNewMetadata()
                .withName(subscriptionName)
                .withNamespace(operatorNamespace)
                .endMetadata()
                .withNewSpec()
                .withName(packageName)
                .withSource(catalogSourceName)
                .withSourceNamespace(catalogSourceNamespaceName)
                .withStartingCSV(startingCSV)
                .withChannel(channel)
                .withInstallPlanApproval(installPlanApproval)
                .endSpec()
                .build();

        ((OpenShiftClient) Kubernetes.getClient()).operatorHub().subscriptions().inNamespace(operatorNamespace).create(subscription);

        if(((OpenShiftClient) Kubernetes.getClient()).operatorHub().subscriptions().inNamespace(operatorNamespace).withName(subscriptionName).get() == null) {
            LOGGER.info("Subscription {} in namespace {}: packageName={}, catalogSourceName={}, catalogSourceNamespaceName={}, startingCSV={}, channel={}, installPlanApproval={} is not created.",
                    subscriptionName, operatorNamespace, packageName, catalogSourceName, catalogSourceNamespaceName, startingCSV, channel, installPlanApproval);

            return null;
        } else {
            LOGGER.info("Subscription {} in namespace {}: packageName={}, catalogSourceName={}, catalogSourceNamespaceName={}, startingCSV={}, channel={}, installPlanApproval={} created.",
                    subscriptionName, operatorNamespace, packageName, catalogSourceName, catalogSourceNamespaceName, startingCSV, channel, installPlanApproval);

            return subscription;
        }
    }

    public static void deleteSubscription(Subscription subscription) {
        if(subscription != null) {
            if(((OpenShiftClient) Kubernetes.getClient()).operatorHub().subscriptions().inNamespace(subscription.getMetadata().getNamespace()).withName(subscription.getMetadata().getName()).get() == null) {
                LOGGER.info("Subscription {} in namespace {}: packageName={}, catalogSourceName={}, catalogSourceNamespaceName={}, startingCSV={}, channel={}, installPlanApproval={} already removed.",
                        subscription.getMetadata().getName(), subscription.getMetadata().getNamespace(),  subscription.getSpec().getName(), subscription.getSpec().getSource(), subscription.getSpec().getSourceNamespace(), subscription.getSpec().getStartingCSV(), subscription.getSpec().getChannel(), subscription.getSpec().getInstallPlanApproval());
            } else {
                LOGGER.info("Removing subscription {} in namespace {}: packageName={}, catalogSourceName={}, catalogSourceNamespaceName={}, startingCSV={}, channel={}, installPlanApproval={}...",
                        subscription.getMetadata().getName(), subscription.getMetadata().getNamespace(), subscription.getSpec().getName(), subscription.getSpec().getSource(), subscription.getSpec().getSourceNamespace(), subscription.getSpec().getStartingCSV(), subscription.getSpec().getChannel(), subscription.getSpec().getInstallPlanApproval());

                ((OpenShiftClient) Kubernetes.getClient()).operatorHub().subscriptions().inNamespace(subscription.getMetadata().getNamespace()).withName(subscription.getMetadata().getName()).delete();

                if(subscription.getSpec().getStartingCSV() != "") {
                    LOGGER.info("Removing startingCSV {} in namespace {}...", subscription.getSpec().getStartingCSV(), subscription.getMetadata().getNamespace());

                    ((OpenShiftClient) Kubernetes.getClient()).operatorHub().clusterServiceVersions().inNamespace(subscription.getMetadata().getNamespace()).withName(subscription.getSpec().getStartingCSV()).delete();

                    if(((OpenShiftClient) Kubernetes.getClient()).operatorHub().clusterServiceVersions().inNamespace(subscription.getMetadata().getNamespace()).withName(subscription.getSpec().getStartingCSV()).get() == null) {
                        LOGGER.info("StartingCSV {} in namespace {} removed.", subscription.getSpec().getStartingCSV(), subscription.getMetadata().getNamespace());
                    }
                }
            }
        }
    }

}
