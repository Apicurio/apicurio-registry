package io.apicurio.registry.operator.resource.ui;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.fabric8.kubernetes.api.model.Service;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.apicurio.registry.operator.resource.LabelDiscriminators.UIServiceDiscriminator;
import static io.apicurio.registry.operator.resource.ResourceKey.UI_SERVICE_KEY;
import static io.apicurio.registry.operator.utils.Mapper.toYAML;

@KubernetesDependent(resourceDiscriminator = UIServiceDiscriminator.class)
public class UIServiceResource extends CRUDKubernetesDependentResource<Service, ApicurioRegistry3> {

    private static final Logger log = LoggerFactory.getLogger(UIServiceResource.class);

    public UIServiceResource() {
        super(Service.class);
    }

    @Override
    protected Service desired(ApicurioRegistry3 primary, Context<ApicurioRegistry3> context) {
        var s = UI_SERVICE_KEY.getFactory().apply(primary);
        log.trace("Desired {} is {}", UI_SERVICE_KEY.getId(), toYAML(s));
        return s;
    }
}
