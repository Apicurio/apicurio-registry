package io.apicurio.registry.operator.resource.consoleplugin;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;

import static io.apicurio.registry.operator.api.v1.ContainerNames.CONSOLE_PLUGIN_CONTAINER_NAME;
import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_APP;
import static io.apicurio.registry.operator.resource.ResourceFactory.RESOURCE_TYPE_SERVICE;
import static io.apicurio.registry.operator.resource.ResourceKey.CONSOLE_PLUGIN_DEPLOYMENT_KEY;
import static io.apicurio.registry.operator.resource.app.AppDeploymentResource.addEnvVar;
import static io.apicurio.registry.operator.resource.app.AppDeploymentResource.getContainerFromDeployment;
import static io.apicurio.registry.operator.utils.Mapper.toYAML;

@KubernetesDependent
public class ConsolePluginDeploymentResource extends CRUDKubernetesDependentResource<Deployment, ApicurioRegistry3> {

    private static final Logger log = LoggerFactory.getLogger(ConsolePluginDeploymentResource.class);

    public ConsolePluginDeploymentResource() {
        super(Deployment.class);
    }

    @Override
    protected Deployment desired(ApicurioRegistry3 primary, Context<ApicurioRegistry3> context) {
        var d = CONSOLE_PLUGIN_DEPLOYMENT_KEY.getFactory().apply(primary);

        var envVars = new LinkedHashMap<String, EnvVar>();

        var appServiceName = primary.getMetadata().getName() + "-" + COMPONENT_APP + "-" + RESOURCE_TYPE_SERVICE;
        var registryApiUrl = "http://" + appServiceName + "." + primary.getMetadata().getNamespace() + ".svc:8080";
        addEnvVar(envVars, new EnvVarBuilder().withName("REGISTRY_API_URL").withValue(registryApiUrl).build());

        var container = getContainerFromDeployment(d, CONSOLE_PLUGIN_CONTAINER_NAME);
        if (container.getEnv() == null) {
            container.setEnv(new ArrayList<>());
        }
        container.getEnv().addAll(envVars.values());

        log.trace("Desired {} is {}", CONSOLE_PLUGIN_DEPLOYMENT_KEY.getId(), toYAML(d));
        return d;
    }
}
