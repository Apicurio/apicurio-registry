package io.apicurio.registry.operator.resource.ui;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.resource.LabelDiscriminators.UINetworkPolicyDiscriminator;
import io.fabric8.kubernetes.api.model.networking.v1.NetworkPolicy;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_UI;
import static io.apicurio.registry.operator.resource.ResourceKey.UI_NETWORK_POLICY_KEY;
import static io.apicurio.registry.operator.utils.Mapper.toYAML;

// spotless:off
@KubernetesDependent(
        labelSelector = "app.kubernetes.io/name=apicurio-registry,app.kubernetes.io/component=" + COMPONENT_UI,
        resourceDiscriminator = UINetworkPolicyDiscriminator.class
)
// spotless:on
public class UINetworkPolicyResource
        extends CRUDKubernetesDependentResource<NetworkPolicy, ApicurioRegistry3> {

    private static final Logger log = LoggerFactory.getLogger(UINetworkPolicyResource.class);

    public UINetworkPolicyResource() {
        super(NetworkPolicy.class);
    }

    @Override
    protected NetworkPolicy desired(ApicurioRegistry3 primary, Context<ApicurioRegistry3> context) {
        var networkPolicy = UI_NETWORK_POLICY_KEY.getFactory().apply(primary);
        log.debug("Desired {} is {}", UI_NETWORK_POLICY_KEY.getId(), toYAML(networkPolicy));
        return networkPolicy;
    }
}
