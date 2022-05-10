package io.apicurio.registry.systemtest.operator.types;

import io.apicurio.registry.systemtest.framework.Environment;
import io.apicurio.registry.systemtest.framework.OperatorUtils;
import io.apicurio.registry.systemtest.platform.Kubernetes;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpec;
import io.fabric8.kubernetes.api.model.apps.DeploymentStatus;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.FileInputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

public class ApicurioRegistryBundleOperatorType extends Operator implements OperatorType {
    private final String operatorNamespace;

    private List<HasMetadata> operatorResources;

    public void loadOperatorResourcesFromFile() throws Exception {
        LOGGER.info("Loading operator resources from file " + getSource() + "...");

        if(getSource().startsWith("http://") || getSource().startsWith("https://")) {
            Path tmpPath = Environment.getTempPath(
                    "apicurio-registry-bundle-operator-install-" + Instant.now().getEpochSecond() + ".yaml"
            );

            LOGGER.info("Downloading file " + getSource() + " to " + tmpPath + "...");

            OperatorUtils.downloadFile(getSource(), tmpPath);

            LOGGER.info("Using file " + tmpPath + " to load operator resources...");

            operatorResources = Kubernetes.getClient().load(new FileInputStream(tmpPath.toString())).get();

            LOGGER.info("Operator resources loaded from file " + tmpPath + ".");
        } else if(getSource().endsWith(".yaml") || getSource().endsWith(".yml")) {
            LOGGER.info("Using file " + getSource() + " to load operator resources...");

            operatorResources = Kubernetes.getClient().load(new FileInputStream(getSource())).get();

            LOGGER.info("Operator resources loaded from file " + getSource() + ".");
        } else {
            throw new Exception("Unable to identify file by source " + getSource() + ".");
        }
    }

    public ApicurioRegistryBundleOperatorType() {
        super(Environment.APICURIO_BUNDLE_SOURCE_PATH);

        operatorNamespace = Environment.APICURIO_OPERATOR_NAMESPACE;

        try {
            loadOperatorResourcesFromFile();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public ApicurioRegistryBundleOperatorType(String source) {
        super(source);

        operatorNamespace = Environment.APICURIO_OPERATOR_NAMESPACE;

        try {
            loadOperatorResourcesFromFile();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public OperatorKind getKind() {
        return OperatorKind.APICURIO_REGISTRY_BUNDLE_OPERATOR;
    }

    @Override
    public String getNamespaceName() {
        return this.operatorNamespace;
    }

    @Override
    public String getDeploymentName() {
        Deployment deployment = OperatorUtils.findDeploymentInResourceList(operatorResources);

        if(deployment == null) {
            return null;
        }

        return deployment.getMetadata().getName();
    }

    @Override
    public Deployment getDeployment() {
        Deployment deployment = OperatorUtils.findDeploymentInResourceList(operatorResources);

        if (deployment == null) {
            return null;
        }

        return Kubernetes.getDeployment(Environment.APICURIO_OPERATOR_NAMESPACE, deployment.getMetadata().getName());
    }

    @Override
    public void install(ExtensionContext testContext) {
        Kubernetes.createOrReplaceResources(Environment.APICURIO_OPERATOR_NAMESPACE, operatorResources);
    }

    @Override
    public void uninstall() {
        Kubernetes.deleteResources(Environment.APICURIO_OPERATOR_NAMESPACE, operatorResources);
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
