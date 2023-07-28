package io.apicurio.registry.systemtests.operator.types;

import io.apicurio.registry.systemtests.framework.Environment;
import io.apicurio.registry.systemtests.framework.LoggerUtils;
import io.apicurio.registry.systemtests.framework.OperatorUtils;
import io.apicurio.registry.systemtests.framework.ResourceUtils;
import io.apicurio.registry.systemtests.platform.Kubernetes;
import io.apicurio.registry.systemtests.registryinfra.ResourceManager;
import io.apicurio.registry.systemtests.registryinfra.resources.SubscriptionResourceType;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpec;
import io.fabric8.kubernetes.api.model.apps.DeploymentStatus;
import org.slf4j.Logger;

public class KeycloakOLMOperatorType extends OLMOperator implements OperatorType {
    protected static final Logger LOGGER = LoggerUtils.getLogger();

    public KeycloakOLMOperatorType() {
        super(null, Environment.NAMESPACE, false);
    }

    public KeycloakOLMOperatorType(String source) {
        super(source, Environment.NAMESPACE, false);
    }

    public KeycloakOLMOperatorType(String source, String operatorNamespace) {
        super(source, operatorNamespace, false);
    }

    @Override
    public OperatorKind getKind() {
        return OperatorKind.KEYCLOAK_OLM_OPERATOR;
    }

    @Override
    public String getNamespaceName() {
        return getNamespace();
    }

    @Override
    public String getDeploymentName() {
        return Environment.SSO_PACKAGE;
    }

    @Override
    public Deployment getDeployment() {
        return Kubernetes.getDeployment(getSubscription().getMetadata().getNamespace(), getDeploymentName());
    }

    @Override
    public void install() throws InterruptedException {
        String catalogName = Environment.SSO_CATALOG;
        String catalogNamespace = Environment.CATALOG_NAMESPACE;
        String ssoPackage = Environment.SSO_PACKAGE;
        // Add ability to install operator from source?

        if (Kubernetes.namespaceHasAnyOperatorGroup(getNamespace())) {
            LOGGER.info("Operator group already present in namespace {}.", getNamespace());
        } else {
            setOperatorGroup(OperatorUtils.createOperatorGroup(getNamespace()));
        }

        ResourceUtils.waitPackageManifestExists(catalogName, ssoPackage);

        String channelName = OperatorUtils.getDefaultChannel(catalogName, ssoPackage);
        setClusterServiceVersion(OperatorUtils.getCurrentCSV(catalogName, ssoPackage, channelName));

        LOGGER.info("OLM operator CSV: {}", getClusterServiceVersion());

        setSubscription(SubscriptionResourceType.getDefault(
                "sso-subscription",
                getNamespace(),
                ssoPackage,
                catalogName,
                catalogNamespace,
                getClusterServiceVersion(),
                channelName
        ));

        ResourceManager.getInstance().createSharedResource(true, getSubscription());

        /* Waiting for operator deployment readiness is implemented in OperatorManager. */
    }

    @Override
    public void uninstall() {
        OperatorUtils.deleteSubscription(getSubscription());

        OperatorUtils.deleteClusterServiceVersion(getNamespace(), getClusterServiceVersion());

        if (getOperatorGroup() != null) {
            OperatorUtils.deleteOperatorGroup(getOperatorGroup());
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
