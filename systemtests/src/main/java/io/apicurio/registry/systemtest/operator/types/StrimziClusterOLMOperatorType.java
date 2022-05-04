package io.apicurio.registry.systemtest.operator.types;

import io.apicurio.registry.systemtest.framework.Environment;
import io.apicurio.registry.systemtest.framework.OperatorUtils;
import io.apicurio.registry.systemtest.platform.Kubernetes;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpec;
import io.fabric8.kubernetes.api.model.apps.DeploymentStatus;
import io.fabric8.openshift.api.model.operatorhub.lifecyclemanager.v1.PackageChannel;
import io.fabric8.openshift.api.model.operatorhub.lifecyclemanager.v1.PackageManifest;
import io.fabric8.openshift.api.model.operatorhub.v1.OperatorGroup;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.Subscription;
import io.fabric8.openshift.client.OpenShiftClient;

public class StrimziClusterOLMOperatorType extends Operator implements OperatorType {
    private final String operatorNamespace;
    private final boolean isClusterWide;
    private Subscription subscription = null;
    private OperatorGroup operatorGroup = null;

    public StrimziClusterOLMOperatorType(String source, String operatorNamespace, boolean isClusterWide) {
        super(source);

        if(isClusterWide) {
            // Static set of cluster wide operator namespace
            this.operatorNamespace = Environment.OLM_CLUSTER_WIDE_NAMESPACE;
        } else {
            this.operatorNamespace = operatorNamespace;
        }

        this.isClusterWide = isClusterWide;
    }

    @Override
    public OperatorKind getKind() {
        return OperatorKind.STRIMZI_CLUSTER_OLM_OPERATOR;
    }

    @Override
    public String getNamespaceName() {
        return this.operatorNamespace;
    }

    @Override
    public String getDeploymentName() {
        return Environment.strimziOLMDeploymentName;
    }

    @Override
    public Deployment getDeployment() {
        return Kubernetes.getClient().apps().deployments().inNamespace(subscription.getMetadata().getNamespace()).list().getItems().stream()
                .filter(d -> d.getMetadata().getName().startsWith(getDeploymentName()))
                .findFirst()
                .orElse(null);
    }

    @Override
    public void install() {
        /* Operator namespace is created in OperatorManager. */

        if(isClusterWide) {
            LOGGER.info("Installing cluster wide OLM operator {} in namespace {}...", getKind(), operatorNamespace);
        } else {
            LOGGER.info("Installing namespaced OLM operator {} in namespace {}...", getKind(), operatorNamespace);

            operatorGroup = OperatorUtils.createOperatorGroup(Environment.strimziOLMOperatorGroupName, operatorNamespace);
        }

        String startingCSV = Environment.strimziOLMSubscriptionStartingCSV;

        if(startingCSV.equals("")) {
            PackageManifest packageManifest = ((OpenShiftClient) Kubernetes.getClient()).operatorHub().packageManifests().inNamespace(Environment.strimziOLMCatalogSourceNamespace).withName(Environment.strimziOLMSubscriptionPkg).get();

            for (PackageChannel packageChannel : packageManifest.getStatus().getChannels()) {
                if (packageChannel.getName().equals(Environment.strimziOLMSubscriptionChannel)) {
                    startingCSV = packageChannel.getCurrentCSV();
                }
            }
        }

        subscription = OperatorUtils.createSubscription(
                Environment.strimziOLMSubscriptionName,
                operatorNamespace,
                Environment.strimziOLMSubscriptionPkg,
                Environment.strimziOLMCatalogSourceName,
                Environment.strimziOLMCatalogSourceNamespace,
                startingCSV,
                Environment.strimziOLMSubscriptionChannel,
                Environment.strimziOLMSubscriptionPlanApproval
        );

        /* Waiting for operator deployment readiness is implemented in OperatorManager. */
    }

    @Override
    public void uninstall() {
        OperatorUtils.deleteSubscription(subscription);

        OperatorUtils.deleteOperatorGroup(operatorGroup);

        /* Waiting for operator deployment removal is implemented in OperatorManager. */
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
        return getDeployment() == null;
    }
}
