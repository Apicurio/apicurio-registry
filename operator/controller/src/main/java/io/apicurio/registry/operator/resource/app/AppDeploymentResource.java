package io.apicurio.registry.operator.resource.app;

import io.apicurio.registry.operator.EnvironmentVariables;
import io.apicurio.registry.operator.OperatorException;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3Spec;
import io.apicurio.registry.operator.api.v1.spec.AppFeaturesSpec;
import io.apicurio.registry.operator.api.v1.spec.AppSpec;
import io.apicurio.registry.operator.api.v1.spec.StorageSpec;
import io.apicurio.registry.operator.api.v1.spec.auth.AuthSpec;
import io.apicurio.registry.operator.feat.Cors;
import io.apicurio.registry.operator.feat.KafkaSql;
import io.apicurio.registry.operator.feat.PostgresSql;
import io.apicurio.registry.operator.feat.TLS;
import io.apicurio.registry.operator.feat.security.Auth;
import io.apicurio.registry.operator.status.ReadyConditionManager;
import io.apicurio.registry.operator.status.StatusManager;
import io.apicurio.registry.operator.utils.JavaOptsAppend;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static io.apicurio.registry.operator.api.v1.ContainerNames.REGISTRY_APP_CONTAINER_NAME;
import static io.apicurio.registry.operator.resource.ResourceKey.APP_DEPLOYMENT_KEY;
import static io.apicurio.registry.operator.utils.Mapper.copy;
import static io.apicurio.registry.operator.utils.Mapper.toYAML;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

@KubernetesDependent
public class AppDeploymentResource extends CRUDKubernetesDependentResource<Deployment, ApicurioRegistry3> {

    private static final Logger log = LoggerFactory.getLogger(AppDeploymentResource.class);

    public AppDeploymentResource() {
        super(Deployment.class);
    }

    @Override
    protected Deployment desired(ApicurioRegistry3 _primary, Context<ApicurioRegistry3> context) {
        var primary = copy(_primary);
        StatusManager.get(primary).getConditionManager(ReadyConditionManager.class).recordIsActive(APP_DEPLOYMENT_KEY);
        var deployment = APP_DEPLOYMENT_KEY.getFactory().apply(primary);

        var envVars = new LinkedHashMap<String, EnvVar>();
        ofNullable(primary.getSpec()).map(ApicurioRegistry3Spec::getApp).map(AppSpec::getEnv)
                .ifPresent(env -> env.forEach(e -> envVars.put(e.getName(), e)));

        // Handling of JAVA_OPTS_APPEND env var - we will merge whatever we find in the CR
        // with whatever we might set ourselves in the logic of the operator.
        var javaOptsAppend = new JavaOptsAppend();
        if (envVars.containsKey(EnvironmentVariables.JAVA_OPTS_APPEND)) {
            javaOptsAppend.setOptsFromEnvVar(envVars.get(EnvironmentVariables.JAVA_OPTS_APPEND).getValue());
            envVars.remove(EnvironmentVariables.JAVA_OPTS_APPEND);
        }

        addEnvVar(envVars, new EnvVarBuilder().withName(EnvironmentVariables.QUARKUS_PROFILE).withValue("prod").build());
        addEnvVar(envVars, new EnvVarBuilder().withName(EnvironmentVariables.QUARKUS_HTTP_ACCESS_LOG_ENABLED).withValue("true").build());

        // Enable deletes if configured in the CR
        boolean allowDeletes = Optional.ofNullable(primary.getSpec().getApp())
                .map(AppSpec::getFeatures)
                .map(AppFeaturesSpec::getResourceDeleteEnabled)
                .orElse(Boolean.FALSE);

        if (allowDeletes) {
            addEnvVar(envVars, new EnvVarBuilder().withName(EnvironmentVariables.APICURIO_REST_DELETION_ARTIFACT_VERSION_ENABLED).withValue("true").build());
            addEnvVar(envVars, new EnvVarBuilder().withName(EnvironmentVariables.APICURIO_REST_DELETION_ARTIFACT_ENABLED).withValue("true").build());
            addEnvVar(envVars, new EnvVarBuilder().withName(EnvironmentVariables.APICURIO_REST_DELETION_GROUP_ENABLED).withValue("true").build());
        }

        boolean authEnabled = Optional.ofNullable(primary.getSpec())
                .map(ApicurioRegistry3Spec::getApp)
                .map(AppSpec::getAuth)
                .map(AuthSpec::getEnabled)
                .orElse(Boolean.FALSE);

        //Configure auth when it's enabled
        if (authEnabled) {
            Auth.configureAuth(requireNonNull(ofNullable(primary.getSpec().getApp())
                    .map(AppSpec::getAuth)
                    .orElse(null)), deployment, envVars);
        }

        // Configure the CORS_ALLOWED_ORIGINS env var based on the ingress host
        Cors.configureAllowedOrigins(primary, envVars);

        // Configure the TLS env vars
        TLS.configureTLS(primary, deployment, REGISTRY_APP_CONTAINER_NAME, envVars);

        // Configure the storage (Postgresql or KafkaSql).
        ofNullable(primary.getSpec()).map(ApicurioRegistry3Spec::getApp).map(AppSpec::getStorage)
                .map(StorageSpec::getType).ifPresent(storageType -> {
                    switch (storageType) {
                        case POSTGRESQL -> PostgresSql.configureDatasource(primary, envVars);
                        case KAFKASQL -> KafkaSql.configureKafkaSQL(primary, deployment, envVars);
                    }
                });

        // Set the JAVA_OPTS_APPEND env var that may have been built up
        if (!javaOptsAppend.isEmpty()) {
            envVars.put(EnvironmentVariables.JAVA_OPTS_APPEND, javaOptsAppend.toEnvVar());
        }

        // Set the ENV VARs on the deployment's container spec.
        var container = getContainerFromDeployment(deployment, REGISTRY_APP_CONTAINER_NAME);
        container.setEnv(envVars.values().stream().toList());

        log.trace("Desired {} is {}", APP_DEPLOYMENT_KEY.getId(), toYAML(deployment));
        return deployment;
    }

    public static void addEnvVar(Map<String, EnvVar> map, EnvVar envVar) {
        if (!map.containsKey(envVar.getName())) {
            map.put(envVar.getName(), envVar);
        }
    }

    public static void addEnvVar(Map<String, EnvVar> map, String name, String value) {
        addEnvVar(map, new EnvVarBuilder().withName(name).withValue(value).build());
    }

    /**
     * Get container with a given name from the given Deployment.
     *
     * @throws OperatorException if container was not found
     */
    public static Container getContainerFromDeployment(Deployment d, String name) {
        requireNonNull(d);
        requireNonNull(name);
        log.trace("Getting container {} in Deployment {}", name, ResourceID.fromResource(d));
        if (d.getSpec() != null & d.getSpec().getTemplate() != null) {
            var c = getContainerFromPodTemplateSpec(d.getSpec().getTemplate(), name);
            if (c != null) {
                return c;
            }
        }
        throw new OperatorException(
                "Container %s not found in Deployment %s".formatted(name, ResourceID.fromResource(d)));
    }

    /**
     * Get container with a given name from the given PTS.
     *
     * @return null when container was not found
     */
    public static Container getContainerFromPodTemplateSpec(PodTemplateSpec pts, String name) {
        requireNonNull(pts);
        requireNonNull(name);
        if (pts.getSpec() != null && pts.getSpec().getContainers() != null) {
            for (var c : pts.getSpec().getContainers()) {
                if (name.equals(c.getName())) {
                    return c;
                }
            }
        }
        return null;
    }

}
