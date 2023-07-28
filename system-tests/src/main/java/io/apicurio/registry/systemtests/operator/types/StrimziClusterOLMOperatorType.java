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

public class StrimziClusterOLMOperatorType extends OLMOperator implements OperatorType {
    protected static final Logger LOGGER = LoggerUtils.getLogger();

    public StrimziClusterOLMOperatorType() {
        super(null, Environment.CLUSTER_WIDE_NAMESPACE, true);
    }

    public StrimziClusterOLMOperatorType(boolean isClusterWide) {
        super(
                null,
                isClusterWide ? Environment.CLUSTER_WIDE_NAMESPACE : Environment.NAMESPACE,
                isClusterWide
        );
    }

    public StrimziClusterOLMOperatorType(String source, String operatorNamespace, boolean isClusterWide) {
        super(
                source,
                isClusterWide ? Environment.CLUSTER_WIDE_NAMESPACE : operatorNamespace,
                isClusterWide
        );
    }

    @Override
    public OperatorKind getKind() {
        return OperatorKind.STRIMZI_OLM_OPERATOR;
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
    public void install() throws InterruptedException {
        /* Operator namespace is created in OperatorManager. */

        String catalogName = Environment.CATALOG;
        String catalogNamespace = Environment.CATALOG_NAMESPACE;
        String kafkaPackage = Environment.KAFKA_PACKAGE;

        if (getClusterWide()) {
            LOGGER.info("Installing cluster wide OLM operator {} in namespace {}...", getKind(), getNamespace());
        } else {
            LOGGER.info("Installing namespaced OLM operator {} in namespace {}...", getKind(), getNamespace());
            if (!Kubernetes.namespaceHasAnyOperatorGroup(getNamespace())) {
                setOperatorGroup(OperatorUtils.createOperatorGroup(getNamespace()));
            }
        }

        ResourceUtils.waitPackageManifestExists(catalogName, kafkaPackage);

        String channelName = OperatorUtils.getDefaultChannel(catalogName, kafkaPackage);
        setClusterServiceVersion(OperatorUtils.getCurrentCSV(catalogName, kafkaPackage, channelName));

        LOGGER.info("OLM operator CSV: {}", getClusterServiceVersion());

        setSubscription(SubscriptionResourceType.getDefault(
                "kafka-subscription",
                getNamespace(),
                kafkaPackage,
                catalogName,
                catalogNamespace,
                getClusterServiceVersion(),
                channelName
        ));

        ResourceManager.getInstance().createSharedResource( true, getSubscription());

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
