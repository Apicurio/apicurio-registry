package io.apicurio.registry.systemtest.operator.types;

import io.apicurio.registry.systemtest.framework.Constants;
import io.apicurio.registry.systemtest.framework.Environment;
import io.apicurio.registry.systemtest.framework.OperatorUtils;
import io.apicurio.registry.systemtest.platform.Kubernetes;
import io.apicurio.registry.systemtest.registryinfra.ResourceManager;
import io.apicurio.registry.systemtest.registryinfra.resources.CatalogSourceResourceType;
import io.apicurio.registry.systemtest.registryinfra.resources.SubscriptionResourceType;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpec;
import io.fabric8.kubernetes.api.model.apps.DeploymentStatus;
import io.fabric8.openshift.api.model.operatorhub.lifecyclemanager.v1.PackageManifest;
import io.fabric8.openshift.api.model.operatorhub.v1.OperatorGroup;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.CatalogSource;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.Subscription;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.text.MessageFormat;


public class ApicurioRegistryOLMOperatorType extends Operator implements OperatorType {
    private final String operatorNamespace;
    private final boolean isClusterWide;
    private Subscription subscription = null;
    private OperatorGroup operatorGroup = null;
    private CatalogSource catalogSource = null;

    public ApicurioRegistryOLMOperatorType(boolean isClusterWide) {
        super(Environment.CATALOG_IMAGE);

        if (isClusterWide) {
            // Static set of cluster wide operator namespace
            this.operatorNamespace = Constants.CLUSTER_WIDE_NAMESPACE;
        } else {
            this.operatorNamespace = Constants.TESTSUITE_NAMESPACE;
        }

        this.isClusterWide = isClusterWide;
    }

    public ApicurioRegistryOLMOperatorType(String source, boolean isClusterWide) {
        super(source);

        if (isClusterWide) {
            // Static set of cluster wide operator namespace
            this.operatorNamespace = Constants.CLUSTER_WIDE_NAMESPACE;
        } else {
            this.operatorNamespace = Constants.TESTSUITE_NAMESPACE;
        }

        this.isClusterWide = isClusterWide;
    }

    public ApicurioRegistryOLMOperatorType(boolean isClusterWide, String operatorNamespace) {
        super(Environment.CATALOG_IMAGE);

        if (isClusterWide) {
            // Static set of cluster wide operator namespace
            this.operatorNamespace = Constants.CLUSTER_WIDE_NAMESPACE;
        } else {
            this.operatorNamespace = operatorNamespace;
        }

        this.isClusterWide = isClusterWide;
    }

    public ApicurioRegistryOLMOperatorType(String source, boolean isClusterWide, String operatorNamespace) {
        super(source);

        if (isClusterWide) {
            // Static set of cluster wide operator namespace
            this.operatorNamespace = Constants.CLUSTER_WIDE_NAMESPACE;
        } else {
            this.operatorNamespace = operatorNamespace;
        }

        this.isClusterWide = isClusterWide;
    }

    /**
     * Create catalog source and wait for its creation.
     */
    private void createCatalogSource(ExtensionContext testContext, String namespace) {
        String name = "testsuite-operators";
        String info = MessageFormat.format("{0} in namespace {1} with image {2}", name, namespace, getSource());

        LOGGER.info("Creating catalog source {}...", info);

        catalogSource = CatalogSourceResourceType.getDefault(name, namespace, getSource());

        ResourceManager.getInstance().createResource(testContext, false, catalogSource);

        LOGGER.info("Waiting for catalog source {} to be created...", info);
        OperatorUtils.waitCatalogSourceExists(namespace, name);

        LOGGER.info("Waiting for pod(s) of catalog source {} to be created...", info);
        OperatorUtils.waitPodsExist(namespace, "olm.catalogSource", name);

        LOGGER.info("Deleting pod(s) of catalog source {}...", info);
        Kubernetes.deletePods(namespace, "olm.catalogSource", name);

        if (Kubernetes.getPods(namespace, "olm.catalogSource", name).getItems().size() != 0) {
            LOGGER.warn("Pod(s) of catalog source {} is/are not deleted.", info);
        }

        LOGGER.info("Waiting for pod(s) of catalog source {} to be ready...", info);
        OperatorUtils.waitPodsReady(namespace, "olm.catalogSource", name);

        LOGGER.info("Waiting for catalog source {} to be ready...", info);
        OperatorUtils.waitCatalogSourceReady(namespace, name);
    }

    private void deleteCatalogSource() {
        if (catalogSource != null) {
            String namespace = catalogSource.getMetadata().getNamespace();
            String name = catalogSource.getMetadata().getName();

            if (Kubernetes.getCatalogSource(namespace, name) == null) {
                LOGGER.info("Catalog source {} in namespace {} already removed.", name, namespace);
            } else {
                LOGGER.info("Removing catalog source {} in namespace {}...", name, namespace);

                Kubernetes.deleteCatalogSource(namespace, name);

                // TODO: Wait for removal?
            }
        }
    }

    @Override
    public OperatorKind getKind() {
        return OperatorKind.APICURIO_REGISTRY_OLM_OPERATOR;
    }

    @Override
    public String getNamespaceName() {
        return operatorNamespace;
    }

    @Override
    public String getDeploymentName() {
        return Constants.REGISTRY_OPERATOR_DEPLOYMENT;
    }

    @Override
    public Deployment getDeployment() {
        return Kubernetes.getDeployment(subscription.getMetadata().getNamespace(), getDeploymentName());
    }

    private String getChannel() {
        PackageManifest packageManifest = Kubernetes.getPackageManifest(
                Constants.CATALOG_NAMESPACE,
                Environment.REGISTRY_PACKAGE
        );

        return packageManifest.getStatus().getDefaultChannel();
    }

    private String getStartingCSV(String channelName) {
        PackageManifest packageManifest = Kubernetes.getPackageManifest(
                Constants.CATALOG_NAMESPACE,
                Environment.REGISTRY_PACKAGE
        );

        return OperatorUtils.getChannelsCurrentCSV(packageManifest, channelName);
    }

    @Override
    public void install(ExtensionContext testContext) {
        /* Operator namespace is created in OperatorManager. */

        String scope = isClusterWide ? "cluster wide" : "namespaced";

        LOGGER.info("Installing {} OLM operator {} in namespace {}...", scope, getKind(), operatorNamespace);

        if (getSource() != null) {
            createCatalogSource(testContext, Constants.CATALOG_NAMESPACE);
        }

        if (!isClusterWide && !OperatorUtils.namespaceHasAnyOperatorGroup(operatorNamespace)) {
            operatorGroup = OperatorUtils.createOperatorGroup(testContext, operatorNamespace);
        }

        String channelName = getChannel();
        String startingCSV = getStartingCSV(channelName);
        String catalogName = catalogSource == null ? Environment.CATALOG : catalogSource.getMetadata().getName();

        subscription = SubscriptionResourceType.getDefault(
                "registry-subscription",
                operatorNamespace,
                Environment.REGISTRY_PACKAGE,
                catalogName,
                Constants.CATALOG_NAMESPACE,
                startingCSV,
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

        if (getSource() != null) {
            deleteCatalogSource();
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
        // Maybe check all operator parts: catalogSourceNamespace, catalogSource, operatorGroup, subscription
        return getDeployment() == null;
    }
}
