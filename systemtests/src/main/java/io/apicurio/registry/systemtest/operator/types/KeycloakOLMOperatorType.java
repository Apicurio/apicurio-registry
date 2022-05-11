package io.apicurio.registry.systemtest.operator.types;

import io.apicurio.registry.systemtest.framework.Constants;
import io.apicurio.registry.systemtest.framework.Environment;
import io.apicurio.registry.systemtest.framework.OperatorUtils;
import io.apicurio.registry.systemtest.platform.Kubernetes;
import io.apicurio.registry.systemtest.registryinfra.ResourceManager;
import io.apicurio.registry.systemtest.registryinfra.resources.SubscriptionResourceType;
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

    public KeycloakOLMOperatorType() {
        super(null);

        this.operatorNamespace = Constants.TESTSUITE_NAMESPACE;
    }

    public KeycloakOLMOperatorType(String source) {
        super(source);

        this.operatorNamespace = Constants.TESTSUITE_NAMESPACE;
    }

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
        return Environment.SSO_PACKAGE;
    }

    @Override
    public Deployment getDeployment() {
        return Kubernetes.getDeployment(subscription.getMetadata().getNamespace(), getDeploymentName());
    }

    @Override
    public void install(ExtensionContext testContext) {
        // Add ability to install operator from source?

        if (OperatorUtils.namespaceHasAnyOperatorGroup(operatorNamespace)) {
            LOGGER.info("Operator group already present in namespace {}.", operatorNamespace);
        } else {
            operatorGroup = OperatorUtils.createOperatorGroup(testContext, operatorNamespace);
        }

        PackageManifest packageManifest = Kubernetes.getPackageManifest(
                Environment.SSO_CATALOG_NAMESPACE,
                Environment.SSO_PACKAGE
        );

        String channelName = packageManifest.getStatus().getDefaultChannel();
        String channelCSV = OperatorUtils.getChannelsCurrentCSV(packageManifest, channelName);

        subscription = SubscriptionResourceType.getDefault(
                "sso-subscription",
                operatorNamespace,
                Environment.SSO_PACKAGE,
                Environment.SSO_CATALOG,
                Environment.SSO_CATALOG_NAMESPACE,
                channelCSV,
                channelName
        );

        ResourceManager.getInstance().createResource(testContext, true, subscription);

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

        DeploymentStatus status = deployment.getStatus();

        if (status == null || status.getReplicas() == null || status.getAvailableReplicas() == null) {
            return false;
        }

        DeploymentSpec spec = deployment.getSpec();

        if (spec == null || spec.getReplicas() == null) {
            return false;
        }

        return spec.getReplicas().intValue() == status.getReplicas()
                && spec.getReplicas() <= status.getAvailableReplicas();
    }

    @Override
    public boolean doesNotExist() {
        return getDeployment() == null;
    }
}
