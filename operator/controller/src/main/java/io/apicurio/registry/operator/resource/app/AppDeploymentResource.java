package io.apicurio.registry.operator.resource.app;

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
import static io.apicurio.registry.operator.resource.LabelDiscriminators.AppDeploymentDiscriminator;
import static io.apicurio.registry.operator.resource.ResourceFactory.APP_CONTAINER_NAME;
import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_APP;
import static io.apicurio.registry.operator.resource.ResourceKey.APP_DEPLOYMENT_KEY;
import static io.apicurio.registry.operator.utils.TraverseUtils.where;

// spotless:off
@KubernetesDependent(
        labelSelector = "app.kubernetes.io/name=apicurio-registry,app.kubernetes.io/component=" + COMPONENT_APP,
        resourceDiscriminator = AppDeploymentDiscriminator.class
)
// spotless:on
public class AppDeploymentResource extends CRUDKubernetesDependentResource<Deployment, ApicurioRegistry3> {

    private static final Logger log = LoggerFactory.getLogger(AppDeploymentResource.class);

    public AppDeploymentResource() {
        super(Deployment.class);
    }

    @Override
    protected Deployment desired(ApicurioRegistry3 primary, Context<ApicurioRegistry3> context) {
        try (var ru = new ResourceUtils<>(primary, context, APP_DEPLOYMENT_KEY)) {

            var ec = new EnvCache();
            ec.addFromPrimary(primary.getSpec().getApp().getEnv());

            ec.add("QUARKUS_PROFILE", "prod", OPERATOR);
            ec.add("APICURIO_CONFIG_CACHE_ENABLED", "true", OPERATOR);

            ec.add("QUARKUS_HTTP_ACCESS_LOG_ENABLED", "true", OPERATOR);
            ec.add("QUARKUS_HTTP_CORS_ORIGINS", "*", OPERATOR);

            ec.add("APICURIO_REST_DELETION_GROUP_ENABLED", "true", OPERATOR);
            ec.add("APICURIO_REST_DELETION_ARTIFACT_ENABLED", "true", OPERATOR);
            ec.add("APICURIO_REST_DELETION_ARTIFACTVERSION_ENABLED", "true", OPERATOR);

            ec.add("APICURIO_APIS_V2_DATE_FORMAT", "yyyy-MM-dd''T''HH:mm:ssZ", OPERATOR);

            ru.withDesiredResource(d -> {
                where(d.getSpec().getTemplate().getSpec().getContainers(),
                        c -> APP_CONTAINER_NAME.equals(c.getName()), c -> {
                            c.setEnv(ec.getEnv());
                        });
            });

            return ru.returnDesiredResource();
        }
    }
}
