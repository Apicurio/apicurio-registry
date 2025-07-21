package io.apicurio.registry.operator.resource.ui;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3Spec;
import io.apicurio.registry.operator.api.v1.spec.UiSpec;
import io.apicurio.registry.operator.status.ReadyConditionManager;
import io.apicurio.registry.operator.status.StatusManager;
import io.apicurio.registry.operator.utils.Utils;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;

import static io.apicurio.registry.operator.api.v1.ContainerNames.REGISTRY_UI_CONTAINER_NAME;
import static io.apicurio.registry.operator.resource.ResourceKey.*;
import static io.apicurio.registry.operator.resource.app.AppDeploymentResource.addEnvVar;
import static io.apicurio.registry.operator.resource.app.AppDeploymentResource.getContainerFromDeployment;
import static io.apicurio.registry.operator.utils.IngressUtils.withIngressRule;
import static io.apicurio.registry.operator.utils.Mapper.copy;
import static io.apicurio.registry.operator.utils.Mapper.toYAML;
import static java.util.Optional.ofNullable;

@KubernetesDependent
public class UIDeploymentResource extends CRUDKubernetesDependentResource<Deployment, ApicurioRegistry3> {

    private static final Logger log = LoggerFactory.getLogger(UIDeploymentResource.class);

    public UIDeploymentResource() {
        super(Deployment.class);
    }

    @Override
    protected Deployment desired(ApicurioRegistry3 _primary, Context<ApicurioRegistry3> context) {
        var primary = copy(_primary);
        StatusManager.get(primary).getConditionManager(ReadyConditionManager.class).recordIsActive(UI_DEPLOYMENT_KEY);
        var d = UI_DEPLOYMENT_KEY.getFactory().apply(primary);

        var envVars = new LinkedHashMap<String, EnvVar>();
        ofNullable(primary.getSpec()).map(ApicurioRegistry3Spec::getUi).map(UiSpec::getEnv)
                .ifPresent(env -> env.forEach(e -> envVars.put(e.getName(), e)));

        var sOpt = Utils.getSecondaryResource(context, primary, APP_SERVICE_KEY);
        sOpt.ifPresent(s -> {
            var iOpt = Utils.getSecondaryResource(context, primary, APP_INGRESS_KEY);
            iOpt.ifPresent(i -> withIngressRule(s, i, rule -> {
                addEnvVar(envVars, new EnvVarBuilder().withName("REGISTRY_API_URL").withValue("http://%s/apis/registry/v3".formatted(rule.getHost())).build());
            }));
        });

        var container = getContainerFromDeployment(d, REGISTRY_UI_CONTAINER_NAME);
        container.setEnv(envVars.values().stream().toList());

        log.trace("Desired {} is {}", UI_DEPLOYMENT_KEY.getId(), toYAML(d));
        return d;
    }
}
