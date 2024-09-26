package io.apicurio.registry.operator.resource;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.resource.app.AppDeploymentDiscriminator;
import io.apicurio.registry.operator.resource.app.AppServiceDiscriminator;
import io.apicurio.registry.operator.resource.ui.UIDeploymentDiscriminator;
import io.apicurio.registry.operator.resource.ui.UIServiceDiscriminator;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.api.reconciler.ResourceDiscriminator;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.function.Function;

@AllArgsConstructor
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class ResourceKey<R> {

    // spotless:off
    public static final String REGISTRY_ID = "ApicurioRegistry3Reconciler";

    public static final String APP_DEPLOYMENT_ID = "AppDeploymentResource";
    public static final String UI_DEPLOYMENT_ID = "UIDeploymentResource";

    public static final String APP_SERVICE_ID = "AppServiceResource";
    public static final String UI_SERVICE_ID = "UIServiceResource";

    public static final ResourceKey<ApicurioRegistry3> REGISTRY_KEY = new ResourceKey<>(
            REGISTRY_ID, ApicurioRegistry3.class,
            null, null
    );

    public static final ResourceKey<Deployment> APP_DEPLOYMENT_KEY = new ResourceKey<>(
            APP_DEPLOYMENT_ID, Deployment.class,
            AppDeploymentDiscriminator.INSTANCE, ResourceFactory.INSTANCE::getDefaultAppDeployment
    );

    public static final ResourceKey<Deployment> UI_DEPLOYMENT_KEY = new ResourceKey<>(
            UI_DEPLOYMENT_ID, Deployment.class,
            UIDeploymentDiscriminator.INSTANCE, ResourceFactory.INSTANCE::getDefaultUIDeployment
    );

    public static final ResourceKey<Service> APP_SERVICE_KEY = new ResourceKey<>(
            APP_SERVICE_ID, Service.class,
            AppServiceDiscriminator.INSTANCE, ResourceFactory.INSTANCE::getDefaultAppService
    );

    public static final ResourceKey<Service> UI_SERVICE_KEY = new ResourceKey<>(
            UI_SERVICE_ID, Service.class,
            UIServiceDiscriminator.INSTANCE, ResourceFactory.INSTANCE::getDefaultUIService
    );
    // spotless:on

    @EqualsAndHashCode.Include
    @ToString.Include
    private String id;

    @EqualsAndHashCode.Include
    @ToString.Include
    private Class<R> klass;

    private ResourceDiscriminator<R, ApicurioRegistry3> discriminator;

    private Function<ApicurioRegistry3, R> factory;
}
