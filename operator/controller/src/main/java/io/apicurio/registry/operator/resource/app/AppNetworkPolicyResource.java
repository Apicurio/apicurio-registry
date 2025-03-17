package io.apicurio.registry.operator.resource.app;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3Spec;
import io.apicurio.registry.operator.api.v1.spec.AppSpec;
import io.apicurio.registry.operator.feat.TLS;
import io.apicurio.registry.operator.resource.LabelDiscriminators.AppNetworkPolicyDiscriminator;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.networking.v1.NetworkPolicy;
import io.fabric8.kubernetes.api.model.networking.v1.NetworkPolicyIngressRuleBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_APP;
import static io.apicurio.registry.operator.resource.ResourceKey.APP_NETWORK_POLICY_KEY;
import static io.apicurio.registry.operator.utils.Mapper.toYAML;
import static java.util.Optional.ofNullable;

// spotless:off
@KubernetesDependent(
        labelSelector = "app.kubernetes.io/name=apicurio-registry,app.kubernetes.io/component=" + COMPONENT_APP,
        resourceDiscriminator = AppNetworkPolicyDiscriminator.class
)
// spotless:on
public class AppNetworkPolicyResource
        extends CRUDKubernetesDependentResource<NetworkPolicy, ApicurioRegistry3> {

    private static final Logger log = LoggerFactory.getLogger(AppNetworkPolicyResource.class);

    public AppNetworkPolicyResource() {
        super(NetworkPolicy.class);
    }

    @Override
    protected NetworkPolicy desired(ApicurioRegistry3 primary, Context<ApicurioRegistry3> context) {
        var networkPolicy = APP_NETWORK_POLICY_KEY.getFactory().apply(primary);

        // @formatter:off
        var httpRule = new NetworkPolicyIngressRuleBuilder()
                    .addNewPort()
                        .withProtocol("TCP")
                        .withPort(new IntOrString(8080))
                    .endPort()
                .build();

        var httpsRule = new NetworkPolicyIngressRuleBuilder()
                    .addNewPort()
                        .withProtocol("TCP")
                        .withPort(new IntOrString(8443))
                    .endPort()
                .build();
        // @formatter:on

        var tls = ofNullable(primary.getSpec())
                .map(ApicurioRegistry3Spec::getApp)
                .map(AppSpec::getTls);

        var tlsEnabled = tls.isPresent();
        var insecureRequestsEnabled = tls.map(TLS::insecureRequestsEnabled).orElse(false);

        if (!tlsEnabled || insecureRequestsEnabled) {
            networkPolicy.getSpec().getIngress().add(httpRule);
        }
        if (tlsEnabled) {
            networkPolicy.getSpec().getIngress().add(httpsRule);
        }

        log.trace("Desired {} is {}", APP_NETWORK_POLICY_KEY.getId(), toYAML(networkPolicy));
        return networkPolicy;
    }
}
