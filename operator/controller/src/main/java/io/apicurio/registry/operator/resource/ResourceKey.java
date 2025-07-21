package io.apicurio.registry.operator.resource;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.NetworkPolicy;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudget;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.function.Function;

import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_APP;
import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_UI;

@AllArgsConstructor
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class ResourceKey<R> {

    public static final String REGISTRY_ID = "ApicurioRegistry3Reconciler";

    public static final String APP_DEPLOYMENT_ID = "AppDeploymentResource";
    public static final String APP_SERVICE_ID = "AppServiceResource";
    public static final String APP_INGRESS_ID = "AppIngressResource";
    public static final String APP_POD_DISRUPTION_BUDGET_ID = "AppPodDisruptionBudgetResource";
    public static final String APP_NETWORK_POLICY_ID = "AppNetworkPolicyResource";

    public static final String UI_DEPLOYMENT_ID = "UIDeploymentResource";
    public static final String UI_SERVICE_ID = "UIServiceResource";
    public static final String UI_INGRESS_ID = "UIIngressResource";
    public static final String UI_POD_DISRUPTION_BUDGET_ID = "UIPodDisruptionBudgetResource";
    public static final String UI_NETWORK_POLICY_ID = "UINetworkPolicyResource";

    public static final ResourceKey<ApicurioRegistry3> REGISTRY_KEY = new ResourceKey<>(REGISTRY_ID, ApicurioRegistry3.class, null, null);

    // ===== Registry App

    public static final ResourceKey<Deployment> APP_DEPLOYMENT_KEY = new ResourceKey<>(APP_DEPLOYMENT_ID, Deployment.class, COMPONENT_APP, ResourceFactory.INSTANCE::getDefaultAppDeployment);

    public static final ResourceKey<Service> APP_SERVICE_KEY = new ResourceKey<>(APP_SERVICE_ID, Service.class, COMPONENT_APP, ResourceFactory.INSTANCE::getDefaultAppService);

    public static final ResourceKey<Ingress> APP_INGRESS_KEY = new ResourceKey<>(APP_INGRESS_ID, Ingress.class, COMPONENT_APP, ResourceFactory.INSTANCE::getDefaultAppIngress);

    public static final ResourceKey<NetworkPolicy> APP_NETWORK_POLICY_KEY = new ResourceKey<>(APP_NETWORK_POLICY_ID, NetworkPolicy.class, COMPONENT_APP, ResourceFactory.INSTANCE::getDefaultAppNetworkPolicy);

    public static final ResourceKey<PodDisruptionBudget> APP_POD_DISRUPTION_BUDGET_KEY = new ResourceKey<>(APP_POD_DISRUPTION_BUDGET_ID, PodDisruptionBudget.class, COMPONENT_APP, ResourceFactory.INSTANCE::getDefaultAppPodDisruptionBudget);

    // ===== Registry UI

    public static final ResourceKey<Deployment> UI_DEPLOYMENT_KEY = new ResourceKey<>(UI_DEPLOYMENT_ID, Deployment.class, COMPONENT_UI, ResourceFactory.INSTANCE::getDefaultUIDeployment);

    public static final ResourceKey<Service> UI_SERVICE_KEY = new ResourceKey<>(UI_SERVICE_ID, Service.class, COMPONENT_UI, ResourceFactory.INSTANCE::getDefaultUIService);

    public static final ResourceKey<Ingress> UI_INGRESS_KEY = new ResourceKey<>(UI_INGRESS_ID, Ingress.class, COMPONENT_UI, ResourceFactory.INSTANCE::getDefaultUIIngress);

    public static final ResourceKey<NetworkPolicy> UI_NETWORK_POLICY_KEY = new ResourceKey<>(UI_NETWORK_POLICY_ID, NetworkPolicy.class, COMPONENT_UI, ResourceFactory.INSTANCE::getDefaultUINetworkPolicy);

    public static final ResourceKey<PodDisruptionBudget> UI_POD_DISRUPTION_BUDGET_KEY = new ResourceKey<>(UI_POD_DISRUPTION_BUDGET_ID, PodDisruptionBudget.class, COMPONENT_UI, ResourceFactory.INSTANCE::getDefaultUIPodDisruptionBudget);

    @EqualsAndHashCode.Include
    @ToString.Include
    private String id;

    @EqualsAndHashCode.Include
    @ToString.Include
    private Class<R> klass;

    private String component;

    private Function<ApicurioRegistry3, R> factory;
}
