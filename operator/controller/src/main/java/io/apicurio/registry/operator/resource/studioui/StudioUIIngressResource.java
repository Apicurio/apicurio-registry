package io.apicurio.registry.operator.resource.studioui;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.resource.LabelDiscriminators.StudioUIIngressDiscriminator;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_STUDIO_UI;
import static io.apicurio.registry.operator.resource.ResourceKey.STUDIO_UI_INGRESS_KEY;
import static io.apicurio.registry.operator.resource.ResourceKey.STUDIO_UI_SERVICE_KEY;
import static io.apicurio.registry.operator.utils.IngressUtils.getHost;
import static io.apicurio.registry.operator.utils.IngressUtils.withIngressRule;
import static io.apicurio.registry.operator.utils.Mapper.toYAML;

@KubernetesDependent(resourceDiscriminator = StudioUIIngressDiscriminator.class)
public class StudioUIIngressResource extends CRUDKubernetesDependentResource<Ingress, ApicurioRegistry3> {

    private static final Logger log = LoggerFactory.getLogger(StudioUIIngressResource.class);

    public StudioUIIngressResource() {
        super(Ingress.class);
    }

    @Override
    protected Ingress desired(ApicurioRegistry3 primary, Context<ApicurioRegistry3> context) {

        var i = STUDIO_UI_INGRESS_KEY.getFactory().apply(primary);

        var sOpt = context.getSecondaryResource(STUDIO_UI_SERVICE_KEY.getKlass(),
                STUDIO_UI_SERVICE_KEY.getDiscriminator());
        sOpt.ifPresent(
                s -> withIngressRule(s, i, rule -> rule.setHost(getHost(COMPONENT_STUDIO_UI, primary))));

        log.trace("Desired {} is {}", STUDIO_UI_INGRESS_KEY.getId(), toYAML(i));
        return i;
    }
}
