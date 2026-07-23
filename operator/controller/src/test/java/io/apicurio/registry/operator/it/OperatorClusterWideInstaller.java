package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.OperatorException;
import io.apicurio.registry.operator.resource.Labels;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.eclipse.microprofile.config.ConfigProvider.getConfig;

final class OperatorClusterWideInstaller {

    private static final Logger log = LoggerFactory.getLogger(OperatorClusterWideInstaller.class);

    static final String OPERATOR_NAMESPACE_PROP = "test.operator.operator-namespace";
    static final String OPERATOR_NAMESPACE_DEFAULT = "apicurio-registry-operator-test";

    private static final String PLACEHOLDER_NAMESPACE = "PLACEHOLDER_NAMESPACE";

    private static boolean installedInThisJvm = false;

    private OperatorClusterWideInstaller() {
    }

    static String operatorNamespace() {
        return getConfig().getOptionalValue(OPERATOR_NAMESPACE_PROP, String.class)
                .orElse(OPERATOR_NAMESPACE_DEFAULT);
    }

    static synchronized void ensureInstalled(KubernetesClient client, String deploymentTarget)
            throws IOException {
        if (installedInThisJvm) {
            return;
        }
        var namespace = operatorNamespace();
        if (isOperatorReady(client, namespace)) {
            log.info("Operator already ready in namespace {}, skipping install", namespace);
            installedInThisJvm = true;
            return;
        }
        log.info("Installing cluster-wide operator into namespace {}", namespace);
        ensureNamespace(client, namespace);
        for (HasMetadata resource : loadInstallFile(namespace, deploymentTarget)) {
            log.info("Creating operator resource kind {} name {}", resource.getKind(),
                    resource.getMetadata().getName());
            await().atMost(ITBase.SHORT_DURATION).ignoreExceptions().untilAsserted(() -> {
                client.resource(resource).inNamespace(namespace).createOrReplace();
                assertThat(client.resource(resource).inNamespace(namespace).get()).isNotNull();
            });
        }
        waitForOperatorReady(client, namespace);
        installedInThisJvm = true;
    }

    private static boolean isOperatorReady(KubernetesClient client, String namespace) {
        var deployments = client.apps().deployments().inNamespace(namespace)
                .withLabels(Labels.getOperatorSelectorLabels()).list().getItems();
        if (deployments.size() != 1) {
            return false;
        }
        var status = deployments.get(0).getStatus();
        if (status == null) {
            return false;
        }
        var readyReplicas = status.getReadyReplicas();
        return readyReplicas != null && readyReplicas > 0;
    }

    private static void ensureNamespace(KubernetesClient client, String namespace) {
        if (client.namespaces().withName(namespace).get() == null) {
            try {
                client.resource(new NamespaceBuilder().withNewMetadata()
                        .addToLabels("app", "apicurio-registry-operator-test-operator").withName(namespace)
                        .endMetadata().build()).create();
            } catch (KubernetesClientException ex) {
                if (ex.getStatus() == null || !"AlreadyExists".equals(ex.getStatus().getReason())) {
                    throw ex;
                }
            }
        }
    }

    private static List<HasMetadata> loadInstallFile(String namespace, String deploymentTarget)
            throws IOException {
        var installFilePath = Path.of(getConfig().getValue(ITBase.REMOTE_TESTS_INSTALL_FILE, String.class));
        try {
            return transformInstallFile(Files.readString(installFilePath), namespace, deploymentTarget);
        } catch (NoSuchFileException ex) {
            throw new OperatorException("Remote tests require an install file to be generated. "
                    + "Please run `make INSTALL_FILE=\"" + installFilePath + "\" dist-install-file` first, "
                    + "or see the README for more information.", ex);
        }
    }

    static List<HasMetadata> transformInstallFile(String rawInstallFile, String namespace,
            String deploymentTarget) {
        List<HasMetadata> resources = Serialization
                .unmarshal(rawInstallFile.replace(PLACEHOLDER_NAMESPACE, namespace));
        resources.forEach(r -> {
            retargetToNamespace(r, namespace);
            if ("minikube".equals(deploymentTarget)) {
                useLocallyBuiltImage(r);
            }
        });
        return resources;
    }

    private static void retargetToNamespace(HasMetadata resource, String namespace) {
        if (resource instanceof ClusterRoleBinding crb && crb.getSubjects() != null) {
            crb.getSubjects().forEach(s -> s.setNamespace(namespace));
        }
    }

    private static void useLocallyBuiltImage(HasMetadata resource) {
        if (resource instanceof Deployment d) {
            d.getSpec().getTemplate().getSpec().getContainers()
                    .forEach(c -> c.setImagePullPolicy("IfNotPresent"));
        }
    }

    private static void waitForOperatorReady(KubernetesClient client, String namespace) {
        await().atMost(ITBase.LONG_DURATION).ignoreExceptions()
                .untilAsserted(() -> assertThat(isOperatorReady(client, namespace)).isTrue());
    }
}
