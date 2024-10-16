package io.apicurio.registry.operator.resource.ui;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;

import static io.apicurio.registry.operator.resource.LabelDiscriminators.UIDeploymentDiscriminator;
import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_UI;
import static io.apicurio.registry.operator.resource.ResourceFactory.UI_CONTAINER_NAME;
import static io.apicurio.registry.operator.resource.ResourceKey.*;
import static io.apicurio.registry.operator.resource.app.AppDeploymentResource.addEnvVar;
import static io.apicurio.registry.operator.resource.app.AppDeploymentResource.getContainer;
import static io.apicurio.registry.operator.utils.IngressUtils.withIngressRule;
import static io.apicurio.registry.operator.utils.Mapper.toYAML;

// spotless:off
@KubernetesDependent(
        labelSelector = "app.kubernetes.io/name=apicurio-registry,app.kubernetes.io/component=" + COMPONENT_UI,
        resourceDiscriminator = UIDeploymentDiscriminator.class
)
// spotless:on
public class UIDeploymentResource extends CRUDKubernetesDependentResource<Deployment, ApicurioRegistry3> {

    private static final Logger log = LoggerFactory.getLogger(UIDeploymentResource.class);

    public UIDeploymentResource() {
        super(Deployment.class);
    }

    @Override
    protected Deployment desired(ApicurioRegistry3 primary, Context<ApicurioRegistry3> context) {

        var d = UI_DEPLOYMENT_KEY.getFactory().apply(primary);

        var envVars = new LinkedHashMap<String, EnvVar>();
        primary.getSpec().getUi().getEnv().forEach(e -> {
            envVars.put(e.getName(), e);
        });

        var sOpt = context.getSecondaryResource(APP_SERVICE_KEY.getKlass(),
                APP_SERVICE_KEY.getDiscriminator());
        sOpt.ifPresent(s -> {
            var iOpt = context.getSecondaryResource(APP_INGRESS_KEY.getKlass(),
                    APP_INGRESS_KEY.getDiscriminator());
            iOpt.ifPresent(i -> withIngressRule(s, i, rule -> {
                // spotless:off
                addEnvVar(envVars, new EnvVarBuilder().withName("REGISTRY_API_URL").withValue("http://%s/apis/registry/v3".formatted(rule.getHost())).build());
                // spotless:on
            }));
        });

        var container = getContainer(d, UI_CONTAINER_NAME);
        container.setEnv(envVars.values().stream().toList());

        log.debug("Desired {} is {}", UI_DEPLOYMENT_KEY.getId(), toYAML(d));
        return d;
    }
}
