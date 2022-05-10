package io.apicurio.registry.systemtest.operator.types;

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
        return Environment.STRIMZI_OLM_DEPLOYMENT_NAME;
    }

    @Override
    public Deployment getDeployment() {
        return Kubernetes.getDeploymentByPrefix(subscription.getMetadata().getNamespace(), getDeploymentName());
    }

    @Override
    public void install(ExtensionContext testContext) {
        /* Operator namespace is created in OperatorManager. */

        if(isClusterWide) {
            LOGGER.info("Installing cluster wide OLM operator {} in namespace {}...", getKind(), operatorNamespace);
        } else {
            LOGGER.info("Installing namespaced OLM operator {} in namespace {}...", getKind(), operatorNamespace);

            if(!OperatorUtils.namespaceHasAnyOperatorGroup(operatorNamespace)) {
                operatorGroup = OperatorUtils.createOperatorGroup(testContext, operatorNamespace);
            }
        }

        PackageManifest packageManifest = Kubernetes.getPackageManifest(
                Environment.STRIMZI_OLM_CATALOG_SOURCE_NAMESPACE,
                Environment.STRIMZI_OLM_SUBSCRIPTION_PKG
        );

        String channelName = packageManifest.getStatus().getDefaultChannel();
        String channelCSV = OperatorUtils.getChannelsCurrentCSV(packageManifest, channelName);

        subscription = SubscriptionResourceType.getDefault(
                Environment.STRIMZI_OLM_SUBSCRIPTION_NAME,
                operatorNamespace,
                Environment.STRIMZI_OLM_SUBSCRIPTION_PKG,
                Environment.STRIMZI_OLM_CATALOG_SOURCE_NAME,
                Environment.STRIMZI_OLM_CATALOG_SOURCE_NAMESPACE,
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
