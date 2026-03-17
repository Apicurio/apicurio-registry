package io.apicurio.registry.operator.feat;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3Spec;
import io.apicurio.registry.operator.api.v1.spec.AppSpec;
import io.apicurio.registry.operator.api.v1.spec.StorageSpec;
import io.fabric8.kubernetes.api.model.EnvVar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static io.apicurio.registry.operator.EnvironmentVariables.*;
import static io.apicurio.registry.operator.resource.app.AppDeploymentResource.addEnvVar;
import static io.apicurio.registry.operator.utils.Utils.isBlank;
import static java.util.Optional.ofNullable;

public class KubernetesOps {

    private static final Logger log = LoggerFactory.getLogger(KubernetesOps.class);

    public static void configureKubernetesOps(ApicurioRegistry3 primary, Map<String, EnvVar> env) {
        ofNullable(primary.getSpec()).map(ApicurioRegistry3Spec::getApp).map(AppSpec::getStorage)
                .map(StorageSpec::getKubernetesops).ifPresent(k8sOps -> {

                    addEnvVar(env, APICURIO_STORAGE_KIND, "kubernetesops");
                    addEnvVar(env, APICURIO_FEATURES_EXPERIMENTAL_ENABLED, "true");

                    if (!isBlank(k8sOps.getRegistryId())) {
                        addEnvVar(env, APICURIO_KUBERNETESOPS_ID, k8sOps.getRegistryId());
                    }

                    if (!isBlank(k8sOps.getNamespace())) {
                        addEnvVar(env, APICURIO_KUBERNETESOPS_NAMESPACE, k8sOps.getNamespace());
                    }

                    if (!isBlank(k8sOps.getRefreshEvery())) {
                        addEnvVar(env, APICURIO_KUBERNETESOPS_REFRESH_EVERY, k8sOps.getRefreshEvery());
                    }

                    if (!isBlank(k8sOps.getLabelRegistryId())) {
                        addEnvVar(env, APICURIO_KUBERNETESOPS_LABEL_REGISTRY_ID,
                                k8sOps.getLabelRegistryId());
                    }

                    if (k8sOps.getWatchEnabled() != null) {
                        addEnvVar(env, APICURIO_KUBERNETESOPS_WATCH_ENABLED,
                                k8sOps.getWatchEnabled().toString());
                    }

                    if (!isBlank(k8sOps.getWatchReconnectDelay())) {
                        addEnvVar(env, APICURIO_KUBERNETESOPS_WATCH_RECONNECT_DELAY,
                                k8sOps.getWatchReconnectDelay());
                    }

                    log.debug("KubernetesOps storage configured with registry ID: {}",
                            k8sOps.getRegistryId());
                });
    }
}
