package io.apicurio.registry.systemtests.operator.types;

import io.apicurio.registry.systemtests.framework.Constants;
import io.apicurio.registry.systemtests.framework.Environment;
import io.apicurio.registry.systemtests.framework.LoggerUtils;
import io.apicurio.registry.systemtests.framework.OperatorUtils;
import io.apicurio.registry.systemtests.framework.ResourceUtils;
import io.apicurio.registry.systemtests.platform.Kubernetes;
import io.apicurio.registry.systemtests.registryinfra.ResourceManager;
import io.apicurio.registry.systemtests.registryinfra.resources.CatalogSourceResourceType;
import io.apicurio.registry.systemtests.registryinfra.resources.SubscriptionResourceType;
import io.apicurio.registry.systemtests.time.TimeoutBudget;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpec;
import io.fabric8.kubernetes.api.model.apps.DeploymentStatus;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.CatalogSource;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.Subscription;
import org.junit.jupiter.api.Assertions;
import org.slf4j.Logger;

import java.text.MessageFormat;
import java.time.Duration;


public class ApicurioRegistryOLMOperatorType extends OLMOperator implements OperatorType {
    protected static final Logger LOGGER = LoggerUtils.getLogger();
    private CatalogSource catalogSource = null;

    public ApicurioRegistryOLMOperatorType() {
        super(Environment.CATALOG_IMAGE, Environment.CLUSTER_WIDE_NAMESPACE, true);
    }

    public ApicurioRegistryOLMOperatorType(boolean isClusterWide) {
        super(
                Environment.CATALOG_IMAGE,
                isClusterWide ? Environment.CLUSTER_WIDE_NAMESPACE : Environment.NAMESPACE,
                isClusterWide
        );
    }

    public ApicurioRegistryOLMOperatorType(String source, boolean isClusterWide) {
        super(
                source,
                isClusterWide ? Environment.CLUSTER_WIDE_NAMESPACE : Environment.NAMESPACE,
                isClusterWide
        );
    }

    public ApicurioRegistryOLMOperatorType(boolean isClusterWide, String operatorNamespace) {
        super(
                Environment.CATALOG_IMAGE,
                isClusterWide ? Environment.CLUSTER_WIDE_NAMESPACE : operatorNamespace,
                isClusterWide
        );
    }

    public ApicurioRegistryOLMOperatorType(String source, boolean isClusterWide, String operatorNamespace) {
        super(
                source,
                isClusterWide ? Environment.CLUSTER_WIDE_NAMESPACE : operatorNamespace,
                isClusterWide
        );
    }

    /**
     * Create catalog source and wait for its creation.
     */
    private void createCatalogSource(String namespace) throws InterruptedException {
        String name = Constants.CATALOG_NAME;
        String info = MessageFormat.format("{0} in namespace {1} with image {2}", name, namespace, getSource());

        LOGGER.info("Creating catalog source {}...", info);

        catalogSource = CatalogSourceResourceType.getDefault(name, namespace, getSource());

        ResourceManager.getInstance().createResource(false, catalogSource);

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
        return OperatorKind.REGISTRY_OLM_OPERATOR;
    }

    @Override
    public String getNamespaceName() {
        return getNamespace();
    }

    @Override
    public String getDeploymentName() {
        return Constants.REGISTRY_OPERATOR_DEPLOYMENT;
    }

    @Override
    public Deployment getDeployment() {
        return Kubernetes.getDeployment(getSubscription().getMetadata().getNamespace(), getDeploymentName());
    }

    @Override
    public void install() throws InterruptedException {
        /* Operator namespace is created in OperatorManager. */

        String scope = getClusterWide() ? "cluster wide" : "namespaced";
        String catalogName = Environment.CATALOG;
        String catalogNamespace = Environment.CATALOG_NAMESPACE;
        String registryPackage = Environment.REGISTRY_PACKAGE;

        LOGGER.info("Installing {} OLM operator {} in namespace {}...", scope, getKind(), getNamespace());

        if (getSource() != null) {
            createCatalogSource(catalogNamespace);

            catalogName = catalogSource.getMetadata().getName();
        }

        if (!getClusterWide() && !Kubernetes.namespaceHasAnyOperatorGroup(getNamespace())) {
            setOperatorGroup(OperatorUtils.createOperatorGroup(getNamespace()));
        }

        ResourceUtils.waitPackageManifestExists(catalogName, registryPackage);

        String channelName = OperatorUtils.getDefaultChannel(catalogName, registryPackage);
        setClusterServiceVersion(OperatorUtils.getCurrentCSV(catalogName, registryPackage, channelName));

        LOGGER.info("OLM operator CSV: {}", getClusterServiceVersion());

        setSubscription(SubscriptionResourceType.getDefault(
                "registry-subscription",
                getNamespace(),
                registryPackage,
                catalogName,
                catalogNamespace,
                getClusterServiceVersion(),
                channelName
        ));

        ResourceManager.getInstance().createResource(true, getSubscription());

        /* Waiting for operator deployment readiness is implemented in OperatorManager. */
    }

    @Override
    public void uninstall() {
        OperatorUtils.deleteSubscription(getSubscription());

        OperatorUtils.deleteClusterServiceVersion(getNamespace(), getClusterServiceVersion());

        if (getOperatorGroup() != null) {
            OperatorUtils.deleteOperatorGroup(getOperatorGroup());
        }

        if (getSource() != null) {
            deleteCatalogSource();
        }

        /* Waiting for operator deployment removal is implemented in OperatorManager. */
    }

    public boolean waitReady() {
        TimeoutBudget timeoutBudget = TimeoutBudget.ofDuration(Duration.ofMinutes(7));

        while (!timeoutBudget.timeoutExpired()) {
            if (isReady()) {
                return true;
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();

                return false;
            }
        }

        return isReady();
    }

    public void upgrade() throws InterruptedException {
        LOGGER.info("Upgrading {} {} operator...", getClusterWide() ? "cluster wide" : "namespaced", getKind());

        // Get current subscription namespace
        String subNamespace = getSubscription().getMetadata().getNamespace();
        // Get current subscription name
        String subName = getSubscription().getMetadata().getName();
        // Get namespace of current's subscription catalog
        String catalogNamespace = getSubscription().getSpec().getSourceNamespace();

        LOGGER.info(
                "CSV before upgrade: {}", Kubernetes.getSubscription(subNamespace, subName).getStatus().getCurrentCSV()
        );

        // Update operator source (set it to image with catalog)
        setSource(Environment.CATALOG_IMAGE);

        // Create new catalog source from image
        createCatalogSource(catalogNamespace);

        // Update subscription to use newly created catalog source
        getSubscription().getSpec().setSource(catalogSource.getMetadata().getName());

        // Replace subscription of operator
        Kubernetes.createOrReplaceSubscription(subNamespace, getSubscription());

        // Wait for update of subscription (it points to CSV from new catalog source)
        Assertions.assertTrue(
                waitSubscriptionCurrentCSV(catalogSource.getMetadata().getName()),
                MessageFormat.format(
                        "Timed out waiting for subscription {0} to have new current ClusterServiceVersion.",
                        MessageFormat.format("{0} in namespace {1}", subName, subNamespace)
                )
        );

        // Get updated subscription
        Subscription newSubscription = Kubernetes.getSubscription(subNamespace, subName);
        // Get CSV of updated subscription
        String newCSV = newSubscription.getStatus().getCurrentCSV();

        LOGGER.info("CSV after upgrade: {}", newCSV);

        // Update subscription of operator
        setSubscription(newSubscription);

        // Update CSV of operator
        setClusterServiceVersion(newCSV);

        // Wait for creation of new CSV and its readiness
        Assertions.assertTrue(
                waitClusterServiceVersionReady(),
                MessageFormat.format("New CSV {0} failed readiness check.", newCSV)
        );

        // Wait for operator readiness
        Assertions.assertTrue(waitReady(), "Operator failed readiness check after upgrade.");
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
