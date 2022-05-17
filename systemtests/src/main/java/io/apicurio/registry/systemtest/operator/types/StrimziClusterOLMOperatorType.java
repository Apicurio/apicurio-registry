package io.apicurio.registry.systemtest.operator.types;

import io.apicurio.registry.systemtest.framework.Constants;
import io.apicurio.registry.systemtest.framework.Environment;
import io.apicurio.registry.systemtest.framework.OperatorUtils;
import io.apicurio.registry.systemtest.framework.ResourceUtils;
import io.apicurio.registry.systemtest.platform.Kubernetes;
import io.apicurio.registry.systemtest.registryinfra.ResourceManager;
import io.apicurio.registry.systemtest.registryinfra.resources.SubscriptionResourceType;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpec;
import io.fabric8.kubernetes.api.model.apps.DeploymentStatus;
import org.junit.jupiter.api.extension.ExtensionContext;

public class StrimziClusterOLMOperatorType extends OLMOperator implements OperatorType {

    public StrimziClusterOLMOperatorType(boolean isClusterWide) {
        super(
                null,
                isClusterWide ? Constants.CLUSTER_WIDE_NAMESPACE : Constants.TESTSUITE_NAMESPACE,
                isClusterWide
        );
    }

    public StrimziClusterOLMOperatorType(String source, String operatorNamespace, boolean isClusterWide) {
        super(
                source,
                isClusterWide ? Constants.CLUSTER_WIDE_NAMESPACE : operatorNamespace,
                isClusterWide
        );
    }

    @Override
    public OperatorKind getKind() {
        return OperatorKind.STRIMZI_CLUSTER_OLM_OPERATOR;
    }

    @Override
    public String getNamespaceName() {
        return getNamespace();
    }

    @Override
    public String getDeploymentName() {
        return Environment.KAFKA_DEPLOYMENT;
    }

    @Override
    public Deployment getDeployment() {
        return Kubernetes.getDeploymentByPrefix(getSubscription().getMetadata().getNamespace(), getDeploymentName());
    }

    @Override
    public void install(ExtensionContext testContext) {
        /* Operator namespace is created in OperatorManager. */

        String catalogName = Environment.CATALOG;
        String catalogNamespace = Constants.CATALOG_NAMESPACE;
        String kafkaPackage = Environment.KAFKA_PACKAGE;

        if (getClusterWide()) {
            LOGGER.info("Installing cluster wide OLM operator {} in namespace {}...", getKind(), getNamespace());
        } else {
            LOGGER.info("Installing namespaced OLM operator {} in namespace {}...", getKind(), getNamespace());

            if (!OperatorUtils.namespaceHasAnyOperatorGroup(getNamespace())) {
                setOperatorGroup(OperatorUtils.createOperatorGroup(testContext, getNamespace()));
            }
        }

        ResourceUtils.waitPackageManifestExists(catalogName, kafkaPackage);

        String channelName = OperatorUtils.getDefaultChannel(catalogName, kafkaPackage);
        setClusterServiceVersion(OperatorUtils.getCurrentCSV(catalogName, kafkaPackage, channelName));

        setSubscription(SubscriptionResourceType.getDefault(
                "kafka-subscription",
                getNamespace(),
                kafkaPackage,
                catalogName,
                catalogNamespace,
                getClusterServiceVersion(),
                channelName
        ));

        ResourceManager.getInstance().createResource(testContext, true, getSubscription());

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
