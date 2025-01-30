package io.apicurio.registry.operator.resource.studioui;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3Spec;
import io.apicurio.registry.operator.api.v1.spec.StudioUiSpec;
import io.apicurio.registry.operator.resource.LabelDiscriminators.StudioUIDeploymentDiscriminator;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;

import static io.apicurio.registry.operator.api.v1.ContainerNames.STUDIO_UI_CONTAINER_NAME;
import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_STUDIO_UI;
import static io.apicurio.registry.operator.resource.ResourceKey.*;
import static io.apicurio.registry.operator.resource.app.AppDeploymentResource.addEnvVar;
import static io.apicurio.registry.operator.resource.app.AppDeploymentResource.getContainerFromDeployment;
import static io.apicurio.registry.operator.utils.IngressUtils.withIngressRule;
import static io.apicurio.registry.operator.utils.Mapper.toYAML;
import static java.util.Optional.ofNullable;

@KubernetesDependent(
        labelSelector = "app.kubernetes.io/name=apicurio-registry,app.kubernetes.io/component=" + COMPONENT_STUDIO_UI,
        resourceDiscriminator = StudioUIDeploymentDiscriminator.class
)
public class StudioUIDeploymentResource
        extends CRUDKubernetesDependentResource<Deployment, ApicurioRegistry3> {

    private static final Logger log = LoggerFactory.getLogger(StudioUIDeploymentResource.class);

    public StudioUIDeploymentResource() {
        super(Deployment.class);
    }

    @Override
    protected Deployment desired(ApicurioRegistry3 primary, Context<ApicurioRegistry3> context) {

        var d = STUDIO_UI_DEPLOYMENT_KEY.getFactory().apply(primary);

        var envVars = new LinkedHashMap<String, EnvVar>();
        ofNullable(primary.getSpec()).map(ApicurioRegistry3Spec::getStudioUi).map(StudioUiSpec::getEnv)
                .ifPresent(env -> env.forEach(e -> envVars.put(e.getName(), e)));

        var sOpt = context.getSecondaryResource(APP_SERVICE_KEY.getKlass(),
                APP_SERVICE_KEY.getDiscriminator());
        sOpt.ifPresent(s -> {
            var iOpt = context.getSecondaryResource(APP_INGRESS_KEY.getKlass(),
                    APP_INGRESS_KEY.getDiscriminator());
            iOpt.ifPresent(i -> withIngressRule(s, i, rule -> {
                addEnvVar(envVars, new EnvVarBuilder().withName("APICURIO_REGISTRY_API_URL").withValue("http://%s/apis/registry/v3".formatted(rule.getHost())).build());
            }));
        });

        sOpt = context.getSecondaryResource(UI_SERVICE_KEY.getKlass(), UI_SERVICE_KEY.getDiscriminator());
        sOpt.ifPresent(s -> {
            var iOpt = context.getSecondaryResource(UI_INGRESS_KEY.getKlass(),
                    UI_INGRESS_KEY.getDiscriminator());
            iOpt.ifPresent(i -> withIngressRule(s, i, rule -> {
                addEnvVar(envVars, new EnvVarBuilder().withName("APICURIO_REGISTRY_UI_URL").withValue("http://%s".formatted(rule.getHost())).build());
            }));
        });

        var container = getContainerFromDeployment(d, STUDIO_UI_CONTAINER_NAME);
        container.setEnv(envVars.values().stream().toList());

        log.debug("Desired {} is {}", STUDIO_UI_DEPLOYMENT_KEY.getId(), toYAML(d));
        return d;
    }
}
