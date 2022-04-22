package io.apicurio.registry.systemtest.operator.types;

import io.apicurio.registry.systemtest.framework.Constants;
import io.apicurio.registry.systemtest.framework.OperatorUtils;
import io.apicurio.registry.systemtest.platform.Kubernetes;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpec;
import io.fabric8.kubernetes.api.model.apps.DeploymentStatus;

import java.io.FileInputStream;
import java.time.Instant;
import java.util.List;

public class ApicurioRegistryBundleOperatorType extends Operator implements OperatorType {
    private final String operatorNamespace;

    private List<HasMetadata> operatorResources;

    public void loadOperatorResourcesFromFile() throws Exception {
        operatorLogger.info("Loading operator resources from file " + source + "...");

        if(source.startsWith("http://") || source.startsWith("https://")) {
            String tmpPath = "/tmp/apicurio-registry-bundle-operator-install-" + Instant.now().getEpochSecond() + ".yaml";

            operatorLogger.info("Downloading file " + source + " to " + tmpPath + "...");

            OperatorUtils.downloadFile(source, tmpPath);

            operatorLogger.info("Using file " + tmpPath + " to load operator resources...");

            operatorResources = Kubernetes.getClient().load(new FileInputStream(tmpPath)).get();

            operatorLogger.info("Operator resources loaded from file " + tmpPath + ".");
        } else if(source.endsWith(".yaml") || source.endsWith(".yml")) {
            operatorLogger.info("Using file " + source + " to load operator resources...");

            operatorResources = Kubernetes.getClient().load(new FileInputStream(source)).get();

            operatorLogger.info("Operator resources loaded from file " + source + ".");
        } else {
            throw new Exception("Unable to identify file by source " + source + ".");
        }
    }

    public ApicurioRegistryBundleOperatorType() {
        super(System.getenv().getOrDefault(Constants.APICURIO_REGISTRY_BUNDLE_OPERATOR_SOURCE_PATH_ENV_VARIABLE, Constants.APICURIO_REGISTRY_BUNDLE_OPERATOR_SOURCE_PATH_DEFAULT_VALUE));

        operatorNamespace = OperatorUtils.getApicurioRegistryOperatorNamespace();

        try {
            loadOperatorResourcesFromFile();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public ApicurioRegistryBundleOperatorType(String source) {
        super(source);

        operatorNamespace = OperatorUtils.getApicurioRegistryOperatorNamespace();

        try {
            loadOperatorResourcesFromFile();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getKind() {
        return OperatorKind.APICURIO_REGISTRY_BUNDLE_OPERATOR;
    }

    @Override
    public String getNamespaceName() {
        return this.operatorNamespace;
    }

    @Override
    public String getDeploymentName() {
        Deployment deployment = OperatorUtils.findDeployment(operatorResources);

        if(deployment == null) {
            return null;
        }

        return deployment.getMetadata().getName();
    }

    @Override
    public Deployment getDeployment() {
        Deployment deployment = OperatorUtils.findDeployment(operatorResources);

        if (deployment == null) {
            return null;
        }

        return Kubernetes.getClient().apps().deployments().inNamespace(OperatorUtils.getApicurioRegistryOperatorNamespace()).withName(deployment.getMetadata().getName()).get();
    }

    @Override
    public void install() {
        Kubernetes.getClient().resourceList(operatorResources).inNamespace(OperatorUtils.getApicurioRegistryOperatorNamespace()).createOrReplace();
    }

    @Override
    public void uninstall() {
        Kubernetes.getClient().resourceList(operatorResources).inNamespace(OperatorUtils.getApicurioRegistryOperatorNamespace()).delete();
    }

    @Override
    public boolean isReady() {
        Deployment deployment = getDeployment();

        if (deployment == null) {
            return false;
        }

        DeploymentSpec deploymentSpec = deployment.getSpec();
        DeploymentStatus deploymentStatus = deployment.getStatus();

        if (deploymentStatus == null || deploymentStatus.getReplicas() == null || deploymentStatus.getAvailableReplicas() == null) {
            return false;
        }

        if (deploymentSpec == null || deploymentSpec.getReplicas() == null) {
            return false;
        }

        return deploymentSpec.getReplicas().intValue() == deploymentStatus.getReplicas() && deploymentSpec.getReplicas() <= deploymentStatus.getAvailableReplicas();
    }

    @Override
    public boolean doesNotExist() {
        return getDeployment() == null;
    }
}
