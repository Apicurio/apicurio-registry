package io.apicurio.registry.operator.feat;

import io.apicurio.registry.operator.Configuration;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3Spec;
import io.apicurio.registry.operator.api.v1.spec.ConsolePluginSpec;
import io.fabric8.kubernetes.api.model.GenericKubernetesResourceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_CONSOLE_PLUGIN;
import static io.apicurio.registry.operator.resource.ResourceFactory.RESOURCE_TYPE_SERVICE;
import static java.util.Optional.ofNullable;

public class ConsolePluginManager {

    private static final Logger log = LoggerFactory.getLogger(ConsolePluginManager.class);

    private static final String CONSOLE_PLUGIN_API_GROUP = "console.openshift.io";
    private static final String CONSOLE_PLUGIN_API_VERSION = "v1";
    private static final String CONSOLE_PLUGIN_KIND = "ConsolePlugin";
    private static final String CONSOLE_PLUGIN_PLURAL = "consoleplugins";

    private static final AtomicBoolean openshiftDetected = new AtomicBoolean(false);
    private static volatile boolean detectionDone = false;

    private ConsolePluginManager() {
    }

    /**
     * The {@code ConsolePlugin} custom resource is cluster-scoped, unlike the primary
     * {@code ApicurioRegistry3} resource and the other (namespaced) resources the operator manages.
     * The namespace must therefore be included in the name to keep it unique across CRs that share the
     * same name in different namespaces.
     */
    public static String getPluginName(ApicurioRegistry3 primary) {
        return primary.getMetadata().getNamespace() + "-" + primary.getMetadata().getName()
                + "-console-plugin";
    }

    public static boolean isOpenShift(KubernetesClient client) {
        if (!detectionDone) {
            synchronized (ConsolePluginManager.class) {
                if (!detectionDone) {
                    try {
                        var groups = client.getApiGroups();
                        openshiftDetected.set(groups != null && groups.getGroups().stream()
                                .anyMatch(g -> CONSOLE_PLUGIN_API_GROUP.equals(g.getName())));
                    } catch (Exception e) {
                        log.warn("Failed to detect OpenShift API, assuming plain Kubernetes", e);
                        openshiftDetected.set(false);
                    }
                    detectionDone = true;
                    log.info("OpenShift ConsolePlugin API detected: {}", openshiftDetected.get());
                }
            }
        }
        return openshiftDetected.get();
    }

    public static boolean isEnabled(ApicurioRegistry3 primary) {
        boolean specEnabled = ofNullable(primary.getSpec()).map(ApicurioRegistry3Spec::getConsolePlugin)
                .map(ConsolePluginSpec::getEnabled).orElse(Boolean.FALSE);
        boolean imageConfigured = Configuration.getConsolePluginImage().isPresent();
        return specEnabled && imageConfigured;
    }

    public static void reconcileConsolePluginCR(KubernetesClient client, ApicurioRegistry3 primary) {
        if (!isOpenShift(client) || !isEnabled(primary)) {
            deleteConsolePluginCR(client, primary);
            return;
        }

        var crdContext = newCrdContext();
        deleteLegacyPluginCR(client, primary, crdContext);

        var pluginName = getPluginName(primary);
        var serviceName = primary.getMetadata().getName() + "-" + COMPONENT_CONSOLE_PLUGIN + "-" + RESOURCE_TYPE_SERVICE;
        var namespace = primary.getMetadata().getNamespace();

        var desired = new GenericKubernetesResourceBuilder()
                .withApiVersion(CONSOLE_PLUGIN_API_GROUP + "/" + CONSOLE_PLUGIN_API_VERSION)
                .withKind(CONSOLE_PLUGIN_KIND)
                .withNewMetadata()
                .withName(pluginName)
                .endMetadata()
                .build();

        desired.setAdditionalProperties(Map.of(
                "spec", Map.of(
                        "displayName", "Apicurio Registry",
                        "backend", Map.of(
                                "type", "Service",
                                "service", Map.of(
                                        "name", serviceName,
                                        "namespace", namespace,
                                        "port", 9443,
                                        "basePath", "/"
                                )
                        ),
                        "proxy", List.of(Map.of(
                                "alias", "registry-api",
                                "authorization", "UserToken",
                                "endpoint", Map.of(
                                        "type", "Service",
                                        "service", Map.of(
                                                "name", serviceName,
                                                "namespace", namespace,
                                                "port", 9443
                                        )
                                )
                        ))
                )
        ));

        try {
            var existing = client.genericKubernetesResources(crdContext)
                    .withName(pluginName)
                    .get();

            if (existing == null) {
                client.genericKubernetesResources(crdContext)
                        .resource(desired)
                        .create();
                log.info("Created ConsolePlugin CR: {}", pluginName);
            } else {
                desired.getMetadata().setResourceVersion(existing.getMetadata().getResourceVersion());
                client.genericKubernetesResources(crdContext)
                        .resource(desired)
                        .update();
                log.debug("Updated ConsolePlugin CR: {}", pluginName);
            }
        } catch (Exception e) {
            log.warn("Failed to reconcile ConsolePlugin CR", e);
        }
    }

    public static void deleteConsolePluginCR(KubernetesClient client, ApicurioRegistry3 primary) {
        if (!isOpenShift(client)) {
            return;
        }
        var crdContext = newCrdContext();
        deleteLegacyPluginCR(client, primary, crdContext);

        var pluginName = getPluginName(primary);
        try {
            var existing = client.genericKubernetesResources(crdContext)
                    .withName(pluginName)
                    .get();

            if (existing != null) {
                client.genericKubernetesResources(crdContext)
                        .withName(pluginName)
                        .delete();
                log.info("Deleted ConsolePlugin CR: {}", pluginName);
            }
        } catch (Exception e) {
            log.warn("Failed to delete ConsolePlugin CR", e);
        }
    }

    private static CustomResourceDefinitionContext newCrdContext() {
        return new CustomResourceDefinitionContext.Builder()
                .withGroup(CONSOLE_PLUGIN_API_GROUP)
                .withVersion(CONSOLE_PLUGIN_API_VERSION)
                .withPlural(CONSOLE_PLUGIN_PLURAL)
                .withScope("Cluster")
                .build();
    }

    /**
     * The {@code ConsolePlugin} name used before it was scoped by namespace (see {@link #getPluginName}).
     */
    public static String getLegacyPluginName(ApicurioRegistry3 primary) {
        return primary.getMetadata().getName() + "-console-plugin";
    }

    /**
     * Before the plugin name was scoped by namespace, {@code ConsolePlugin} objects were named
     * {@code <cr-name>-console-plugin}. Clean up any such object left over from before the upgrade so
     * it doesn't stay orphaned on the cluster (it has no owner-reference GC, since the cluster-scoped
     * {@code ConsolePlugin} can't be owned by the namespaced {@code ApicurioRegistry3} CR) pointing at a
     * stale service alongside the new namespace-scoped one.
     */
    private static void deleteLegacyPluginCR(KubernetesClient client, ApicurioRegistry3 primary,
            CustomResourceDefinitionContext crdContext) {
        var legacyName = getLegacyPluginName(primary);
        try {
            var legacy = client.genericKubernetesResources(crdContext)
                    .withName(legacyName)
                    .get();

            if (legacy != null) {
                client.genericKubernetesResources(crdContext)
                        .withName(legacyName)
                        .delete();
                log.info("Deleted legacy ConsolePlugin CR: {}", legacyName);
            }
        } catch (Exception e) {
            log.warn("Failed to delete legacy ConsolePlugin CR", e);
        }
    }
}
