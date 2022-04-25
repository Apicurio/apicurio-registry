package io.apicurio.registry.systemtest.operator.types;

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

public class KeycloakOLMOperatorType extends Operator implements OperatorType {
    private Subscription subscription = null;

    private String operatorNamespace = null;

    private OperatorGroup operatorGroup = null;

    public KeycloakOLMOperatorType(String source, String operatorNamespace) {
        super(source);

        this.operatorNamespace = operatorNamespace;
    }

    @Override
    public String getKind() {
        return OperatorKind.KEYCLOAK_OLM_OPERATOR;
    }

    @Override
    public String getNamespaceName() {
        return operatorNamespace;
    }

    @Override
    public String getDeploymentName() {
        return OperatorUtils.getKeycloakOLMOperatorPackage();
    }

    @Override
    public Deployment getDeployment() {
        return Kubernetes.getClient().apps().deployments().inNamespace(subscription.getMetadata().getNamespace()).withName(getDeploymentName()).get();
    }

    @Override
    public void install() {
        // Add ability to install operator from source?

        if(((OpenShiftClient) Kubernetes.getClient()).operatorHub().operatorGroups().inNamespace(operatorNamespace).list().getItems().size() != 0) {
            operatorLogger.info("Operator group already present in namespace {}.", operatorNamespace);
        } else {
            operatorGroup = OperatorUtils.createOperatorGroup(OperatorUtils.getKeycloakOLMOperatorGroupName(), operatorNamespace);
        }

        PackageManifest packageManifest = ((OpenShiftClient) Kubernetes.getClient()).operatorHub().packageManifests().inNamespace(OperatorUtils.getApicurioRegistryOLMOperatorCatalogSourceNamespace()).withName(OperatorUtils.getKeycloakOLMOperatorPackage()).get();

        String channelName = packageManifest.getStatus().getDefaultChannel();
        String channelCSV = "";


        for(PackageChannel packageChannel : packageManifest.getStatus().getChannels()) {
            if(packageChannel.getName().equals(channelName)) {
                channelCSV = packageChannel.getCurrentCSV();
            }
        }

        subscription = OperatorUtils.createSubscription(
                OperatorUtils.getKeycloakOLMOperatorSubscriptionName(),
                operatorNamespace,
                OperatorUtils.getKeycloakOLMOperatorPackage(),
                OperatorUtils.getKeycloakOLMOperatorCatalogSourceName(),
                OperatorUtils.getApicurioRegistryOLMOperatorCatalogSourceNamespace(),
                channelCSV,
                channelName,
                OperatorUtils.getKeycloakOLMOperatorInstallPlanApproval()
        );

        /**
         * Waiting for operator deployment readiness is implemented in OperatorManager.
         */
    }

    @Override
    public void uninstall() {
        OperatorUtils.deleteSubscription(subscription);

        OperatorUtils.deleteOperatorGroup(operatorGroup);

        /**
         * Waiting for operator deployment removal is implemented in OperatorManager.
         */
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
