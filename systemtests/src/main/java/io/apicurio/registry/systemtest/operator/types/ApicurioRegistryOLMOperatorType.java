package io.apicurio.registry.systemtest.operator.types;

import io.apicurio.registry.systemtest.framework.Environment;
import io.apicurio.registry.systemtest.framework.OperatorUtils;
import io.apicurio.registry.systemtest.platform.Kubernetes;
import io.apicurio.registry.systemtest.registryinfra.ResourceManager;
import io.apicurio.registry.systemtest.registryinfra.resources.CatalogSourceResourceType;
import io.apicurio.registry.systemtest.registryinfra.resources.SubscriptionResourceType;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpec;
import io.fabric8.kubernetes.api.model.apps.DeploymentStatus;
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
    private Namespace catalogSourceNamespace = null;

    public ApicurioRegistryOLMOperatorType(String source, String operatorNamespace, boolean isClusterWide) {
        super(source);

        if(isClusterWide) {
            // Static set of cluster wide operator namespace
            this.operatorNamespace = Environment.OLM_CLUSTER_WIDE_NAMESPACE;
        } else {
            this.operatorNamespace = operatorNamespace;
        }

        this.isClusterWide = isClusterWide;
    }

    private void deleteCatalogSourceNamespace() {
        if (catalogSourceNamespace != null) {
            String catalogSourceNamespaceName = catalogSourceNamespace.getMetadata().getName();

            if (Kubernetes.getNamespace(catalogSourceNamespaceName) == null) {
                LOGGER.info("Catalog source namespace {} already removed.", catalogSourceNamespaceName);
            } else {
                LOGGER.info("Removing catalog source namespace {}...", catalogSourceNamespaceName);

                Kubernetes.deleteNamespace(catalogSourceNamespaceName);

                if (OperatorUtils.waitNamespaceRemoved(catalogSourceNamespaceName)) {
                    LOGGER.info("Catalog source namespace {} removed.", catalogSourceNamespaceName);
                }
            }
        } else {
            LOGGER.info(
                    "Catalog source namespace {} will not be removed, it existed before.",
                    Environment.APICURIO_OLM_CATALOG_SOURCE_NAMESPACE
            );
        }
    }

    /**
     * Create catalog source and wait for its creation.
     */
    private void createCatalogSource(ExtensionContext testContext, String namespace, String name) {
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

        if(Kubernetes.getPods(namespace, "olm.catalogSource", name).getItems().size() != 0) {
            LOGGER.warn("Pod(s) of catalog source {} is/are not deleted.", info);
        }

        LOGGER.info("Waiting for pod(s) of catalog source {} to be ready...", info);
        OperatorUtils.waitPodsReady(namespace, "olm.catalogSource", name);

        LOGGER.info("Waiting for catalog source {} to be ready...", info);
        OperatorUtils.waitCatalogSourceReady(namespace, name);
    }

    private void deleteCatalogSource() {
        if(catalogSource != null) {
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
        return Environment.APICURIO_OLM_DEPLOYMENT_NAME;
    }

    @Override
    public Deployment getDeployment() {
        return Kubernetes.getDeployment(subscription.getMetadata().getNamespace(), getDeploymentName());
    }

    @Override
    public void install(ExtensionContext testContext) {
        /* Operator namespace is created in OperatorManager. */

        if(isClusterWide) {
            LOGGER.info("Installing cluster wide OLM operator {} in namespace {}...", getKind(), operatorNamespace);
        } else {
            LOGGER.info("Installing namespaced OLM operator {} in namespace {}...", getKind(), operatorNamespace);
        }

        if(getSource() != null) {
            createCatalogSource(
                    testContext,
                    Environment.APICURIO_OLM_CATALOG_SOURCE_NAMESPACE,
                    Environment.APICURIO_OLM_CATALOG_SOURCE_NAME
            );
        }

        if(!isClusterWide && !OperatorUtils.namespaceHasAnyOperatorGroup(operatorNamespace)) {
            operatorGroup = OperatorUtils.createOperatorGroup(testContext, operatorNamespace);
        }

        // TODO: Get default channel and current CSV
        // TODO: Check Environment for user defined channel and CSV
        LOGGER.info("TODO: Wait for package manifest to be available here?");

        subscription = SubscriptionResourceType.getDefault(
                Environment.APICURIO_OLM_SUBSCRIPTION_NAME,
                operatorNamespace,
                Environment.APICURIO_OLM_SUBSCRIPTION_PKG,
                Environment.APICURIO_OLM_CATALOG_SOURCE_NAME,
                Environment.APICURIO_OLM_CATALOG_SOURCE_NAMESPACE,
                Environment.APICURIO_OLM_SUBSCRIPTION_STARTING_CSV,
                Environment.APICURIO_OLM_SUBSCRIPTION_CHANNEL
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

        if(getSource() != null) {
            deleteCatalogSource();

            deleteCatalogSourceNamespace();
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
