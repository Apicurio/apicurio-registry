package io.apicurio.registry.operator.resource.studioui;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.resource.LabelDiscriminators.StudioUIServiceDiscriminator;
import io.fabric8.kubernetes.api.model.Service;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_STUDIO_UI;
import static io.apicurio.registry.operator.resource.ResourceKey.STUDIO_UI_SERVICE_KEY;
import static io.apicurio.registry.operator.utils.Mapper.toYAML;

@KubernetesDependent(
        labelSelector = "app.kubernetes.io/name=apicurio-registry,app.kubernetes.io/component=" + COMPONENT_STUDIO_UI,
        resourceDiscriminator = StudioUIServiceDiscriminator.class
)
public class StudioUIServiceResource extends CRUDKubernetesDependentResource<Service, ApicurioRegistry3> {

    private static final Logger log = LoggerFactory.getLogger(StudioUIServiceResource.class);

    public StudioUIServiceResource() {
        super(Service.class);
    }

    @Override
    protected Service desired(ApicurioRegistry3 primary, Context<ApicurioRegistry3> context) {
        var s = STUDIO_UI_SERVICE_KEY.getFactory().apply(primary);
        log.debug("Desired {} is {}", STUDIO_UI_SERVICE_KEY.getId(), toYAML(s));
        return s;
    }
}
