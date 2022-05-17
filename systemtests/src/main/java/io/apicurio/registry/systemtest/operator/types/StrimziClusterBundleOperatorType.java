package io.apicurio.registry.systemtest.operator.types;

import io.apicurio.registry.systemtest.framework.Constants;
import io.apicurio.registry.systemtest.framework.Environment;
import io.apicurio.registry.systemtest.framework.OperatorUtils;
import io.apicurio.registry.systemtest.framework.ResourceUtils;
import io.apicurio.registry.systemtest.platform.Kubernetes;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpec;
import io.fabric8.kubernetes.api.model.apps.DeploymentStatus;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;

public class StrimziClusterBundleOperatorType extends BundleOperator implements OperatorType {

    public void loadOperatorResources() throws Exception {
        LOGGER.info("Loading operator resources from " + getSource() + "...");

        if (getSource().startsWith("http://") || getSource().startsWith("https://")) {
            // Get timestamp
            String timestamp = String.valueOf(Instant.now().getEpochSecond());

            if (getSource().startsWith("https://github.com/")) {
                // Split source string to two values: repo-URL and operator-files-path
                String[] sourceParts = getSource().split(";");
                // Get repo URL
                String repoUrl = sourceParts[0];
                // Get path to operator files inside repo
                String filesPath = sourceParts[1];
                // Get path to clone of repo
                Path clonePath = Environment.getTmpPath("strimzi-bundle-repo-" + timestamp);
                // Clone repo from repo URL to clone repo path
                Git.cloneRepository()
                        .setURI(repoUrl) // Repo URL
                        .setDirectory(clonePath.toFile()) // Repo clone path
                        .call(); // Run cloning

                setResources(Kubernetes.loadFromDirectory(Paths.get(clonePath.toString(), filesPath)));

                ResourceUtils.updateRoleBindingNamespace(getResources(), getNamespace());
            } else {
                Path tmpPath = Environment.getTmpPath("strimzi-bundle-install-" + timestamp + ".yaml");

                OperatorUtils.downloadFile(getSource(), tmpPath);

                LOGGER.info("Using file " + tmpPath + " to load operator resources...");

                setResources(Kubernetes.loadFromFile(tmpPath));

                LOGGER.info("Operator resources loaded from file " + tmpPath + ".");
            }
        } else if (getSource().endsWith(".yaml") || getSource().endsWith(".yml")) {
            LOGGER.info("Using file " + getSource() + " to load operator resources...");

            setResources(Kubernetes.loadFromFile(Path.of(getSource())));

            LOGGER.info("Operator resources loaded from file " + getSource() + ".");
        } else {
            throw new Exception("Unable to identify file by source " + getSource() + ".");
        }
    }

    public StrimziClusterBundleOperatorType() {
        super(Environment.KAFKA_BUNDLE, Constants.TESTSUITE_NAMESPACE);

        try {
            loadOperatorResources();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public StrimziClusterBundleOperatorType(String source) {
        super(source, Constants.TESTSUITE_NAMESPACE);

        try {
            loadOperatorResources();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public OperatorKind getKind() {
        return OperatorKind.STRIMZI_BUNDLE_OPERATOR;
    }

    @Override
    public String getNamespaceName() {
        return getNamespace();
    }

    @Override
    public String getDeploymentName() {
        Deployment deployment = OperatorUtils.findDeploymentInResourceList(getResources());

        if (deployment == null) {
            return null;
        }

        return deployment.getMetadata().getName();
    }

    @Override
    public Deployment getDeployment() {
        Deployment deployment = OperatorUtils.findDeploymentInResourceList(getResources());

        if (deployment == null) {
            return null;
        }

        return Kubernetes.getClient()
                .apps()
                .deployments()
                .inNamespace(getNamespace())
                .withName(deployment.getMetadata().getName())
                .get();
    }

    @Override
    public void install(ExtensionContext testContext) {
        Kubernetes.getClient()
                .resourceList(getResources())
                .inNamespace(getNamespace())
                .createOrReplace();
    }

    @Override
    public void uninstall() {
        Kubernetes.getClient()
                .resourceList(getResources())
                .inNamespace(getNamespace())
                .delete();
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
