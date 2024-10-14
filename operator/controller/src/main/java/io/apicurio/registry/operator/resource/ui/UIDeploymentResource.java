package io.apicurio.registry.operator.resource.ui;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.env.EnvCache;
import io.apicurio.registry.operator.utils.ResourceUtils;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.apicurio.registry.operator.env.EnvCachePriority.OPERATOR;
import static io.apicurio.registry.operator.resource.LabelDiscriminators.UIDeploymentDiscriminator;
import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_UI;
import static io.apicurio.registry.operator.resource.ResourceFactory.UI_CONTAINER_NAME;
import static io.apicurio.registry.operator.resource.ResourceKey.*;
import static io.apicurio.registry.operator.utils.IngressUtils.withIngressRule;
import static io.apicurio.registry.operator.utils.TraverseUtils.where;

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
        try (var ru = new ResourceUtils<>(primary, context, UI_DEPLOYMENT_KEY)) {

            ru.withExistingResource(APP_SERVICE_KEY, appS -> {
                ru.withExistingResource(APP_INGRESS_KEY, appI -> {
                    withIngressRule(appS, appI, rule -> {

                        var ec = new EnvCache();
                        ec.addFromPrimary(primary.getSpec().getUi().getEnv());

                        ec.add("REGISTRY_API_URL", "http://%s/apis/registry/v3".formatted(rule.getHost()),
                                OPERATOR);

                        ru.withDesiredResource(d -> {
                            where(d.getSpec().getTemplate().getSpec().getContainers(),
                                    c -> UI_CONTAINER_NAME.equals(c.getName()), c -> {
                                        c.setEnv(ec.getEnv());
                                    });
                        });
                    });
                });
            });

            return ru.returnDesiredResource();
        }
    }
}
