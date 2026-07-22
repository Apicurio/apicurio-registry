package io.apicurio.registry.operator.resource.consoleplugin;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.fabric8.kubernetes.api.model.Service;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.apicurio.registry.operator.resource.ResourceKey.CONSOLE_PLUGIN_SERVICE_KEY;
import static io.apicurio.registry.operator.utils.Mapper.toYAML;

@KubernetesDependent
public class ConsolePluginServiceResource
        extends CRUDKubernetesDependentResource<Service, ApicurioRegistry3> {

    private static final Logger log = LoggerFactory.getLogger(ConsolePluginServiceResource.class);

    public ConsolePluginServiceResource() {
        super(Service.class);
    }

    @Override
    protected Service desired(ApicurioRegistry3 primary, Context<ApicurioRegistry3> context) {
        var s = CONSOLE_PLUGIN_SERVICE_KEY.getFactory().apply(primary);
        log.trace("Desired {} is {}", CONSOLE_PLUGIN_SERVICE_KEY.getId(), toYAML(s));
        return s;
    }
}
