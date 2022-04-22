package io.apicurio.registry.systemtest.operator.types;

import io.apicurio.registry.systemtest.framework.Constants;
import io.apicurio.registry.systemtest.framework.OperatorUtils;
import io.apicurio.registry.systemtest.platform.Kubernetes;
import io.apicurio.registry.systemtest.time.TimeoutBudget;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpec;
import io.fabric8.kubernetes.api.model.apps.DeploymentStatus;
import io.fabric8.openshift.api.model.operatorhub.v1.OperatorGroup;
import io.fabric8.openshift.api.model.operatorhub.v1.OperatorGroupBuilder;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.CatalogSource;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.CatalogSourceBuilder;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.Subscription;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.SubscriptionBuilder;
import io.fabric8.openshift.client.OpenShiftClient;

import java.time.Duration;

public class ApicurioRegistryOLMOperatorType extends Operator implements OperatorType {

    /*******************************************************************************************************************
        Static values for now.
    *******************************************************************************************************************/
    private String catalogSourceNamespaceName = "openshift-marketplace";
    private String catalogSourceName = "apicurio-registry-catalog-source-e2e-test";
    private String operatorGroupName = "apicurio-registry-operator-group-e2e-test";
    private String subscriptionName = "apicurio-registry-subscription";
    private String packageName = "<package>";
    private String startingCSV = "<csv>";
    private String channel = "<channel>";
    private String installPlanApproval = "Automatic";
    /******************************************************************************************************************/

    private String operatorNamespace = null;
    private boolean isClusterWide = false;
    private Subscription subscription = null;
    private OperatorGroup operatorGroup = null;
    private CatalogSource catalogSource = null;
    private Namespace catalogSourceNamespace = null;

    public ApicurioRegistryOLMOperatorType(String source, String operatorNamespace, boolean isClusterWide) {
        super(source);

        if(isClusterWide) {
            // Static set of cluster wide operator namespace
            this.operatorNamespace = Constants.APICURIO_REGISTRY_OPERATOR_CLUSTER_WIDE_NAMESPACE;
        } else {
            this.operatorNamespace = operatorNamespace;
        }

        this.isClusterWide = isClusterWide;
    }

    private void createCatalogSourceNamespace(String catalogSourceNamespaceName) {
        if(Kubernetes.getClient().namespaces().withName(catalogSourceNamespaceName).get() == null) {
            operatorLogger.info("Creating catalog source namespace {}...", catalogSourceNamespaceName);

            catalogSourceNamespace = new NamespaceBuilder().withNewMetadata().withName(catalogSourceNamespaceName).endMetadata().build();

            Kubernetes.getClient().namespaces().create(catalogSourceNamespace);

            if(OperatorUtils.waitNamespaceReady(catalogSourceNamespaceName)) {
                operatorLogger.info("Catalog source namespace {} is created and ready.", catalogSourceNamespaceName);
            }
        } else {
            operatorLogger.info("Catalog source namespace {} already exists.", catalogSourceNamespaceName);
        }
    }

    private void deleteCatalogSourceNamespace() {
        if(catalogSourceNamespace != null) {
            if (Kubernetes.getClient().namespaces().withName(catalogSourceNamespace.getMetadata().getName()).get() == null) {
                operatorLogger.info("Catalog source namespace {} already removed.", catalogSourceNamespace.getMetadata().getName());
            } else {
                operatorLogger.info("Removing catalog source namespace {}...", catalogSourceNamespace.getMetadata().getName());

                Kubernetes.getClient().namespaces().withName(catalogSourceNamespace.getMetadata().getName()).delete();

                if (OperatorUtils.waitNamespaceRemoved(catalogSourceNamespace.getMetadata().getName())) {
                    operatorLogger.info("Catalog source namespace {} removed.", catalogSourceNamespace.getMetadata().getName());
                }
            }
        } else {
            operatorLogger.info("Catalog source namespace {} will not be removed, it existed before.", catalogSourceNamespaceName);
        }
    }

    private void createCatalogSource(String catalogSourceName, String catalogSourceNamespace) {
        /*
            Create catalog source and wait for its creation.
        */

        operatorLogger.info("Creating catalog source {} in namespace {} with image {}...", catalogSourceName, catalogSourceNamespace, source);

        catalogSource = new CatalogSourceBuilder()
                .withNewMetadata()
                    .withName(catalogSourceName)
                    .withNamespace(catalogSourceNamespace)
                .endMetadata()
                .withNewSpec()
                    .withDisplayName("Apicurio Registry Operator Catalog Source")
                    .withImage(source)
                    .withPublisher("apicurio-registry-qe")
                    .withSourceType("grpc")
                .endSpec()
                .build();

        ((OpenShiftClient) Kubernetes.getClient()).operatorHub().catalogSources().inNamespace(catalogSourceNamespace).create(catalogSource);

        TimeoutBudget timeoutBudgetCatalogSourceCreated = TimeoutBudget.ofDuration(Duration.ofMinutes(3));

        CatalogSource catalogSourceToBeCreated;

        operatorLogger.info("Waiting for catalog source {} in namespace {} with image {} to be created...", catalogSourceName, catalogSourceNamespace, source);

        while (!timeoutBudgetCatalogSourceCreated.timeoutExpired()) {
            catalogSourceToBeCreated = ((OpenShiftClient) Kubernetes.getClient()).operatorHub().catalogSources().inNamespace(catalogSourceNamespace).withName(catalogSourceName).get();

            if (catalogSourceToBeCreated != null) {
                break;
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        catalogSourceToBeCreated = ((OpenShiftClient) Kubernetes.getClient()).operatorHub().catalogSources().inNamespace(catalogSourceNamespace).withName(catalogSourceName).get();

        if(catalogSourceToBeCreated == null) {
            operatorLogger.info("Catalog source {} in namespace {} with image {} is not created.", catalogSourceName, catalogSourceNamespace, source);
        } else {
            operatorLogger.info("Catalog source {} in namespace {} with image {} created.", catalogSourceName, catalogSourceNamespace, source);
        }

        /*
            Wait for catalog source pod(s) to be created.
        */

        operatorLogger.info("Waiting for pod(s) of catalog source {} in namespace {} with image {} to be created...", catalogSourceName, catalogSourceNamespace, source);
        TimeoutBudget timeoutBudgetCatalogSourcePod = TimeoutBudget.ofDuration(Duration.ofMinutes(3));
        PodList podList;

        while (!timeoutBudgetCatalogSourcePod.timeoutExpired()) {
            podList = Kubernetes.getClient().pods().inNamespace(catalogSourceNamespace).withLabel("olm.catalogSource", catalogSourceName).list();

            if (podList.getItems().size() > 0) {
                break;
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        podList = Kubernetes.getClient().pods().inNamespace(catalogSourceNamespace).withLabel("olm.catalogSource", catalogSourceName).list();

        if(podList.getItems().size() == 0) {
            operatorLogger.info("Pod(s) of catalog source {} in namespace {} with image {} is/are not created.", catalogSourceName, catalogSourceNamespace, source);
        } else {
            operatorLogger.info("Pod(s) of catalog source {} in namespace {} with image {} created.", catalogSourceName, catalogSourceNamespace, source);
        }

        /*
            Delete catalog source pod(s).
         */

        operatorLogger.info("Deleting pod(s) of catalog source {} in namespace {} with image {}...", catalogSourceName, catalogSourceNamespace, source);

        Kubernetes.getClient().pods().inNamespace(catalogSourceNamespace).withLabel("olm.catalogSource", catalogSourceName).delete();

        if(Kubernetes.getClient().pods().inNamespace(catalogSourceNamespace).withLabel("olm.catalogSource", catalogSourceName).list().getItems().size() == 0) {
            operatorLogger.info("Pod(s) of catalog source {} in namespace {} with image {} deleted.", catalogSourceName, catalogSourceNamespace, source);
        } else {
            operatorLogger.info("Pod(s) of catalog source {} in namespace {} with image {} is/are not deleted.", catalogSourceName, catalogSourceNamespace, source);
        }

        /*
            Wait for catalog source pod(s) to be ready.
         */

        operatorLogger.info("Waiting for pod(s) of catalog source {} in namespace {} with image {} to be ready...", catalogSourceName, catalogSourceNamespace, source);
        TimeoutBudget timeoutBudgetCatalogSourcePodReady = TimeoutBudget.ofDuration(Duration.ofMinutes(3));
        boolean allPodsReady = true;

        while (!timeoutBudgetCatalogSourcePodReady.timeoutExpired()) {
            allPodsReady = true;

            podList = Kubernetes.getClient().pods().inNamespace(catalogSourceNamespace).withLabel("olm.catalogSource", catalogSourceName).list();

            if(podList.getItems().size() > 0) {
                for (Pod p : podList.getItems()) {
                    boolean podReady = false;

                    if(p.getStatus().getContainerStatuses() != null && p.getStatus().getContainerStatuses().size() > 0) {
                        podReady = p.getStatus().getContainerStatuses().get(0).getReady();
                    }

                    allPodsReady = allPodsReady && podReady;
                }
            } else {
                allPodsReady = false;
            }

            if(allPodsReady) {
                break;
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        if(!allPodsReady) {
            operatorLogger.info("Pod(s) of catalog source {} in namespace {} with image {} are not ready.", catalogSourceName, catalogSourceNamespace, source);
        } else {
            operatorLogger.info("Pod(s) of catalog source {} in namespace {} with image {} ready.", catalogSourceName, catalogSourceNamespace, source);
        }

        /*
            Wait for catalog source to be ready.
         */

        operatorLogger.info("Waiting for catalog source {} in namespace {} with image {} to be ready...", catalogSourceName, catalogSourceNamespace, source);
        TimeoutBudget timeoutBudgetCatalogSourceReady = TimeoutBudget.ofDuration(Duration.ofMinutes(5));
        CatalogSource catalogSourceToBeReady;

        while (!timeoutBudgetCatalogSourceReady.timeoutExpired()) {
            catalogSourceToBeReady = ((OpenShiftClient) Kubernetes.getClient()).operatorHub().catalogSources().inNamespace(catalogSourceNamespace).withName(catalogSourceName).get();

            if(catalogSourceToBeReady != null && catalogSourceToBeReady.getStatus().getConnectionState().getLastObservedState().equals("READY")) {
                break;
            }

            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        catalogSourceToBeReady = ((OpenShiftClient) Kubernetes.getClient()).operatorHub().catalogSources().inNamespace(catalogSourceNamespace).withName(catalogSourceName).get();

        if(catalogSourceToBeReady == null || !catalogSourceToBeReady.getStatus().getConnectionState().getLastObservedState().equals("READY")) {
            operatorLogger.info("Catalog source {} in namespace {} with image {} is not ready.", catalogSourceName, catalogSourceNamespace, source);
        } else {
            operatorLogger.info("Catalog source {} in namespace {} with image {} ready.", catalogSourceName, catalogSourceNamespace, source);
        }
    }

    private void deleteCatalogSource() {
        if(catalogSource != null) {
            if (((OpenShiftClient) Kubernetes.getClient()).operatorHub().catalogSources().inNamespace(catalogSource.getMetadata().getNamespace()).withName(catalogSource.getMetadata().getName()).get() == null) {
                operatorLogger.info("Catalog source {} in namespace {} already removed.", catalogSource.getMetadata().getName(), catalogSource.getMetadata().getNamespace());
            } else {
                operatorLogger.info("Removing catalog source {} in namespace {}...", catalogSource.getMetadata().getName(), catalogSource.getMetadata().getNamespace());

                ((OpenShiftClient) Kubernetes.getClient()).operatorHub().catalogSources().inNamespace(catalogSource.getMetadata().getNamespace()).withName(catalogSource.getMetadata().getName()).delete();

                // Wait for removal?
            }
        }
    }

    private void createOperatorGroup(String operatorGroupName) {
        operatorLogger.info("Creating operator group {} in namespace {} targeting namespace {}...", operatorGroupName, operatorNamespace, operatorNamespace);

        operatorGroup = new OperatorGroupBuilder()
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
            operatorLogger.info("Operator group {} in namespace {} targeting namespace {} is not created.", operatorGroupName, operatorNamespace, operatorNamespace);
        } else {
            operatorLogger.info("Operator group {} in namespace {} targeting namespace {} created.", operatorGroupName, operatorNamespace, operatorNamespace);
        }
    }

    private void deleteOperatorGroup() {
        if(operatorGroup != null) {
            if(((OpenShiftClient) Kubernetes.getClient()).operatorHub().operatorGroups().inNamespace(operatorGroup.getMetadata().getNamespace()).withName(operatorGroup.getMetadata().getName()).get() == null) {
                operatorLogger.info("Operator group {} in namespace {} targeting namespace {} already removed.", operatorGroup.getMetadata().getName(), operatorGroup.getMetadata().getNamespace(), operatorGroup.getSpec().getTargetNamespaces());
            } else {
                operatorLogger.info("Removing operator group {} in namespace {} targeting namespace {}...", operatorGroup.getMetadata().getName(), operatorGroup.getMetadata().getNamespace(), operatorGroup.getSpec().getTargetNamespaces());

                ((OpenShiftClient) Kubernetes.getClient()).operatorHub().operatorGroups().inNamespace(operatorGroup.getMetadata().getNamespace()).withName(operatorGroup.getMetadata().getName()).delete();

                // Wait for removal?
            }
        }
    }

    private void createSubscription(String subscriptionName, String packageName, String catalogSourceName, String catalogSourceNamespaceName, String startingCSV, String channel, String installPlanApproval) {
        operatorLogger.info("Creating subscription {} in namespace {}: packageName={}, catalogSourceName={}, catalogSourceNamespaceName={}, startingCSV={}, channel={}, installPlanApproval={}...",
                subscriptionName, operatorNamespace, packageName, catalogSourceName, catalogSourceNamespaceName, startingCSV, channel, installPlanApproval);

        subscription = new SubscriptionBuilder()
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
            operatorLogger.info("Subscription {} in namespace {}: packageName={}, catalogSourceName={}, catalogSourceNamespaceName={}, startingCSV={}, channel={}, installPlanApproval={} is not created.",
                    subscriptionName, operatorNamespace, packageName, catalogSourceName, catalogSourceNamespaceName, startingCSV, channel, installPlanApproval);
        } else {
            operatorLogger.info("Subscription {} in namespace {}: packageName={}, catalogSourceName={}, catalogSourceNamespaceName={}, startingCSV={}, channel={}, installPlanApproval={} created.",
                    subscriptionName, operatorNamespace, packageName, catalogSourceName, catalogSourceNamespaceName, startingCSV, channel, installPlanApproval);
        }
    }

    private void deleteSubscription() {
        if(subscription != null) {
            if(((OpenShiftClient) Kubernetes.getClient()).operatorHub().subscriptions().inNamespace(subscription.getMetadata().getNamespace()).withName(subscription.getMetadata().getName()).get() == null) {
                operatorLogger.info("Subscription {} in namespace {}: packageName={}, catalogSourceName={}, catalogSourceNamespaceName={}, startingCSV={}, channel={}, installPlanApproval={} already removed.",
                        subscriptionName, operatorNamespace, packageName, catalogSourceName, catalogSourceNamespaceName, startingCSV, channel, installPlanApproval);
            } else {
                operatorLogger.info("Removing subscription {} in namespace {}: packageName={}, catalogSourceName={}, catalogSourceNamespaceName={}, startingCSV={}, channel={}, installPlanApproval={}...",
                        subscriptionName, operatorNamespace, packageName, catalogSourceName, catalogSourceNamespaceName, startingCSV, channel, installPlanApproval);

                ((OpenShiftClient) Kubernetes.getClient()).operatorHub().subscriptions().inNamespace(subscription.getMetadata().getNamespace()).withName(subscription.getMetadata().getName()).delete();

                if(subscription.getSpec().getStartingCSV() != "") {
                    operatorLogger.info("Removing startingCSV {} in namespace {}...", subscription.getSpec().getStartingCSV(), subscription.getMetadata().getNamespace());

                    ((OpenShiftClient) Kubernetes.getClient()).operatorHub().clusterServiceVersions().inNamespace(subscription.getMetadata().getNamespace()).withName(subscription.getSpec().getStartingCSV()).delete();

                    if(((OpenShiftClient) Kubernetes.getClient()).operatorHub().clusterServiceVersions().inNamespace(subscription.getMetadata().getNamespace()).withName(subscription.getSpec().getStartingCSV()).get() == null) {
                        operatorLogger.info("StartingCSV {} in namespace {} removed.", subscription.getSpec().getStartingCSV(), subscription.getMetadata().getNamespace());
                    }
                }
            }
        }
    }

    @Override
    public String getKind() {
        return OperatorKind.APICURIO_REGISTRY_OLM_OPERATOR;
    }

    @Override
    public String getNamespaceName() {
        return operatorNamespace;
    }

    @Override
    public String getDeploymentName() {
        return Constants.APICURIO_REGISTRY_OPERATOR_DEPLOYMENT_NAME;
    }

    @Override
    public Deployment getDeployment() {
        return Kubernetes.getClient().apps().deployments().inNamespace(subscription.getMetadata().getNamespace()).withName(getDeploymentName()).get();
    }

    @Override
    public void install() {
        /**
         * Operator namespace is created in OperatorManager.
         */

        if(isClusterWide) {
            operatorLogger.info("Installing cluster wide OLM operator {} in namespace {}...", getKind(), operatorNamespace);
        } else {
            operatorLogger.info("Installing namespaced OLM operator {} in namespace {}...", getKind(), operatorNamespace);
        }

        createCatalogSourceNamespace(catalogSourceNamespaceName);

        createCatalogSource(catalogSourceName, catalogSourceNamespaceName);

        if(!isClusterWide) {
            createOperatorGroup(operatorGroupName);
        }

        operatorLogger.info("TODO: Wait for package manifest to be available here?");

        createSubscription(subscriptionName, packageName, catalogSourceName, catalogSourceNamespaceName, startingCSV, channel, installPlanApproval);

        /**
         * Waiting for operator deployment readiness is implemented in OperatorManager.
         */
    }

    @Override
    public void uninstall() {
        deleteSubscription();

        deleteOperatorGroup();

        deleteCatalogSource();

        deleteCatalogSourceNamespace();
    }

    @Override
    public boolean isReady() {
        Deployment deployment = getDeployment();

        if (deployment == null) {
            return false;
        }

        DeploymentSpec deploymentSpec = deployment.getSpec();
        DeploymentStatus deploymentStatus = deployment.getStatus();

        if (deploymentStatus == null || deploymentStatus.getReplicas() == null || deploymentStatus.getAvailableReplicas() == null) {
            return false;
        }

        if (deploymentSpec == null || deploymentSpec.getReplicas() == null) {
            return false;
        }

        return deploymentSpec.getReplicas().intValue() == deploymentStatus.getReplicas() && deploymentSpec.getReplicas() <= deploymentStatus.getAvailableReplicas();
    }

    @Override
    public boolean doesNotExist() {
        // Maybe check all operator parts: catalogSourceNamespace, catalogSource, operatorGroup, subscription
        return getDeployment() == null;
    }
}
