package io.apicurio.registry.systemtest.operator.types;

import io.apicurio.registry.systemtest.framework.Environment;
import io.apicurio.registry.systemtest.framework.OperatorUtils;
import io.apicurio.registry.systemtest.platform.Kubernetes;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpec;
import io.fabric8.kubernetes.api.model.apps.DeploymentStatus;
import io.fabric8.openshift.api.model.operatorhub.lifecyclemanager.v1.PackageManifest;
import io.fabric8.openshift.api.model.operatorhub.v1.OperatorGroup;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.Subscription;
import org.junit.jupiter.api.extension.ExtensionContext;

public class KeycloakOLMOperatorType extends Operator implements OperatorType {
    private Subscription subscription = null;
    private final String operatorNamespace;
    private OperatorGroup operatorGroup = null;

    public KeycloakOLMOperatorType(String source, String operatorNamespace) {
        super(source);

        this.operatorNamespace = operatorNamespace;
    }

    @Override
    public OperatorKind getKind() {
        return OperatorKind.KEYCLOAK_OLM_OPERATOR;
    }

    @Override
    public String getNamespaceName() {
        return operatorNamespace;
    }

    @Override
    public String getDeploymentName() {
        return Environment.KEYCLOAK_SUBSCRIPTION_PKG;
    }

    @Override
    public Deployment getDeployment() {
        return Kubernetes.getDeployment(subscription.getMetadata().getNamespace(), getDeploymentName());
    }

    @Override
    public void install(ExtensionContext testContext) {
        // Add ability to install operator from source?

        if(OperatorUtils.namespaceHasAnyOperatorGroup(operatorNamespace)) {
            LOGGER.info("Operator group already present in namespace {}.", operatorNamespace);
        } else {
            operatorGroup = OperatorUtils.createOperatorGroup(operatorNamespace);
        }

        PackageManifest packageManifest = Kubernetes.getPackageManifest(
                Environment.APICURIO_OLM_CATALOG_SOURCE_NAMESPACE,
                Environment.KEYCLOAK_SUBSCRIPTION_PKG
        );

        String channelName = packageManifest.getStatus().getDefaultChannel();
        String channelCSV = OperatorUtils.getChannelsCurrentCSV(packageManifest, channelName);

        subscription = OperatorUtils.createSubscription(
                Environment.KEYCLOAK_SUBSCRIPTION_NAME,
                operatorNamespace,
                Environment.KEYCLOAK_SUBSCRIPTION_PKG,
                Environment.KEYCLOAK_CATALOG_SOURCE_NAME,
                Environment.APICURIO_OLM_CATALOG_SOURCE_NAMESPACE,
                channelCSV,
                channelName
        );

        /* Waiting for operator deployment readiness is implemented in OperatorManager. */
    }

    @Override
    public void uninstall() {
        OperatorUtils.deleteSubscription(subscription);

        if (operatorGroup != null) {
            OperatorUtils.deleteOperatorGroup(operatorGroup);
        }

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

        if (
                deploymentStatus == null
                || deploymentStatus.getReplicas() == null
                || deploymentStatus.getAvailableReplicas() == null
        ) {
            return false;
        }

        if (deploymentSpec == null || deploymentSpec.getReplicas() == null) {
            return false;
        }

        return deploymentSpec.getReplicas().intValue() == deploymentStatus.getReplicas()
                && deploymentSpec.getReplicas() <= deploymentStatus.getAvailableReplicas();
    }

    @Override
    public boolean doesNotExist() {
        return getDeployment() == null;
    }
}
