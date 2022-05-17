package io.apicurio.registry.systemtest.operator.types;

import io.apicurio.registry.systemtest.framework.Constants;
import io.apicurio.registry.systemtest.framework.Environment;
import io.apicurio.registry.systemtest.framework.OperatorUtils;
import io.apicurio.registry.systemtest.framework.ResourceUtils;
import io.apicurio.registry.systemtest.platform.Kubernetes;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpec;
import io.fabric8.kubernetes.api.model.apps.DeploymentStatus;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.FileInputStream;
import java.nio.file.Path;
import java.time.Instant;

public class ApicurioRegistryBundleOperatorType extends BundleOperator implements OperatorType {

    public void loadOperatorResourcesFromFile() throws Exception {
        LOGGER.info("Loading operator resources from file " + getSource() + "...");

        if (getSource().startsWith("http://") || getSource().startsWith("https://")) {
            Path tmpPath = Environment.getTmpPath(
                    "apicurio-registry-bundle-operator-install-" + Instant.now().getEpochSecond() + ".yaml"
            );

            LOGGER.info("Downloading file " + getSource() + " to " + tmpPath + "...");

            OperatorUtils.downloadFile(getSource(), tmpPath);

            LOGGER.info("Using file " + tmpPath + " to load operator resources...");

            setResources(Kubernetes.getClient().load(new FileInputStream(tmpPath.toString())).get());

            LOGGER.info("Operator resources loaded from file " + tmpPath + ".");
        } else if (getSource().endsWith(".yaml") || getSource().endsWith(".yml")) {
            LOGGER.info("Using file " + getSource() + " to load operator resources...");

            setResources(Kubernetes.getClient().load(new FileInputStream(getSource())).get());

            LOGGER.info("Operator resources loaded from file " + getSource() + ".");
        } else {
            throw new Exception("Unable to identify file by source " + getSource() + ".");
        }

        ResourceUtils.updateRoleBindingNamespace(getResources(), getNamespace());
    }

    public ApicurioRegistryBundleOperatorType() {
        super(Environment.REGISTRY_BUNDLE, Constants.TESTSUITE_NAMESPACE);

        try {
            loadOperatorResourcesFromFile();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public ApicurioRegistryBundleOperatorType(String source) {
        super(source, Constants.TESTSUITE_NAMESPACE);

        try {
            loadOperatorResourcesFromFile();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public OperatorKind getKind() {
        return OperatorKind.REGISTRY_BUNDLE_OPERATOR;
    }

    @Override
    public String getNamespaceName() {
        return getNamespace();
    }

    @Override
    public String getDeploymentName() {
        Deployment deployment = OperatorUtils.findDeployment(getResources());

        if (deployment == null) {
            return null;
        }

        return deployment.getMetadata().getName();
    }

    @Override
    public Deployment getDeployment() {
        Deployment deployment = OperatorUtils.findDeployment(getResources());

        if (deployment == null) {
            return null;
        }

        return Kubernetes.getDeployment(getNamespace(), deployment.getMetadata().getName());
    }

    @Override
    public void install(ExtensionContext testContext) {
        Kubernetes.createOrReplaceResources(getNamespace(), getResources());
    }

    @Override
    public void uninstall() {
        Kubernetes.deleteResources(getNamespace(), getResources());
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
