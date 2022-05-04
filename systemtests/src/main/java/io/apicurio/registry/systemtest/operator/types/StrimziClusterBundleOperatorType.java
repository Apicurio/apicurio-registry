package io.apicurio.registry.systemtest.operator.types;

import io.apicurio.registry.systemtest.framework.Environment;
import io.apicurio.registry.systemtest.framework.OperatorUtils;
import io.apicurio.registry.systemtest.platform.Kubernetes;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpec;
import io.fabric8.kubernetes.api.model.apps.DeploymentStatus;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.api.model.rbac.Subject;
import org.eclipse.jgit.api.Git;

import java.io.FileInputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class StrimziClusterBundleOperatorType extends Operator implements OperatorType {
    private final String operatorNamespace;

    private List<HasMetadata> operatorResources;

    public void loadOperatorResourcesFromFile() throws Exception {
        LOGGER.info("Loading operator resources from file " + source + "...");

        if(source.startsWith("http://") || source.startsWith("https://")) {
            if(source.startsWith("https://github.com/")) {
                // Get timestamp
                String timestamp = String.valueOf(Instant.now().getEpochSecond());
                // Split source string to two values: repo-URL and operator-files-path
                String[] sourceParts = source.split(";");
                // Get repo URL
                String repoUrl = sourceParts[0];
                // Get path to operator files inside repo
                String operatorFilesPath = sourceParts[1];
                // Get path to clone of repo
                Path repoClonePath = Paths.get(Environment.tempPath, "strimzi-bundle-repo-" + timestamp);
                // Clone repo from repo URL to clone repo path
                Git.cloneRepository()
                        .setURI(repoUrl) // Repo URL
                        .setDirectory(repoClonePath.toFile()) // Repo clone path
                        .call(); // Run cloning
                // Get list of files in operator files path
                List<String> operatorFiles = OperatorUtils.listFiles(Paths.get(repoClonePath.toString(), operatorFilesPath));
                // Initialize operator resources
                operatorResources = new ArrayList<>();
                // Load operator files to operator resources
                for(String file : operatorFiles) {
                    // Load one operator file and add all resources from file to operator resources
                    operatorResources.addAll(Kubernetes.getClient().load(new FileInputStream(Paths.get(repoClonePath.toString(), operatorFilesPath, file).toString())).get());
                }
                // Go through all loaded operator resources
                for(HasMetadata resource : operatorResources) {
                    // If resource is RoleBinding
                    if(resource.getKind().equals("RoleBinding")) {
                        // Iterate over all subjects in this RoleBinding
                        for(Subject s: ((RoleBinding) resource).getSubjects()) {
                            // Change namespace of subject to operator namespace
                            s.setNamespace(operatorNamespace);
                        }
                    // If resource is ClusterRoleBinding
                    } else if(resource.getKind().equals("ClusterRoleBinding")) {
                        // Iterate over all subjects in this ClusterRoleBinding
                        for(Subject s : ((ClusterRoleBinding) resource).getSubjects()) {
                            // Change namespace of subject to operator namespace
                            s.setNamespace(operatorNamespace);
                        }
                    }
                }
            } else {
                String tmpPath = Paths.get(Environment.tempPath, "strimzi-cluster-bundle-operator-install-" + Instant.now().getEpochSecond() + ".yaml").toString();

                LOGGER.info("Downloading file " + source + " to " + tmpPath + "...");

                OperatorUtils.downloadFile(source, tmpPath);

                LOGGER.info("Using file " + tmpPath + " to load operator resources...");

                operatorResources = Kubernetes.getClient().load(new FileInputStream(tmpPath)).get();

                LOGGER.info("Operator resources loaded from file " + tmpPath + ".");
            }
        } else if(source.endsWith(".yaml") || source.endsWith(".yml")) {
            LOGGER.info("Using file " + source + " to load operator resources...");

            operatorResources = Kubernetes.getClient().load(new FileInputStream(source)).get();

            LOGGER.info("Operator resources loaded from file " + source + ".");
        } else {
            throw new Exception("Unable to identify file by source " + source + ".");
        }
    }

    public StrimziClusterBundleOperatorType() {
        super(System.getenv().getOrDefault(Environment.STRIMZI_OPERATOR_SOURCE_PATH_ENV_VAR, Environment.STRIMZI_OPERATOR_SOURCE_PATH_DEFAULT));

        operatorNamespace = Environment.strimziOperatorNamespace;

        try {
            loadOperatorResourcesFromFile();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public StrimziClusterBundleOperatorType(String source) {
        super(source);

        operatorNamespace = Environment.strimziOperatorNamespace;

        try {
            loadOperatorResourcesFromFile();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public OperatorKind getKind() {
        return OperatorKind.STRIMZI_CLUSTER_BUNDLE_OPERATOR;
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

        return Kubernetes.getClient().apps().deployments().inNamespace(Environment.strimziOperatorNamespace).withName(deployment.getMetadata().getName()).get();
    }

    @Override
    public void install() {
        Kubernetes.getClient().resourceList(operatorResources).inNamespace(Environment.strimziOperatorNamespace).createOrReplace();
    }

    @Override
    public void uninstall() {
        Kubernetes.getClient().resourceList(operatorResources).inNamespace(Environment.strimziOperatorNamespace).delete();
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
