package io.apicurio.registry.operator.resource.consoleplugin;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.ConsolePlugin;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_CONSOLE_PLUGIN;
import static io.apicurio.registry.operator.resource.ResourceFactory.RESOURCE_TYPE_SERVICE;
import static io.apicurio.registry.operator.utils.Mapper.toYAML;

@KubernetesDependent
public class ConsolePluginCRResource extends CRUDKubernetesDependentResource<ConsolePlugin, ApicurioRegistry3> {

    private static final Logger log = LoggerFactory.getLogger(ConsolePluginCRResource.class);

    public ConsolePluginCRResource() {
        super(ConsolePlugin.class);
    }

    @Override
    protected ConsolePlugin desired(ApicurioRegistry3 primary, Context<ApicurioRegistry3> context) {
        var pluginName = primary.getMetadata().getName() + "-" + COMPONENT_CONSOLE_PLUGIN;
        var serviceName = primary.getMetadata().getName() + "-" + COMPONENT_CONSOLE_PLUGIN + "-" + RESOURCE_TYPE_SERVICE;
        var namespace = primary.getMetadata().getNamespace();

        var r = new ConsolePlugin();
        r.setMetadata(new ObjectMeta());
        r.getMetadata().setName(pluginName);

        r.setAdditionalProperty("spec", Map.of(
                "displayName", "Apicurio Registry",
                "backend", Map.of(
                        "type", "Service",
                        "service", Map.of(
                                "name", serviceName,
                                "namespace", namespace,
                                "port", 9443,
                                "basePath", "/"
                        )
                ),
                "proxy", List.of(Map.of(
                        "alias", "registry-api",
                        "authorization", "UserToken",
                        "endpoint", Map.of(
                                "type", "Service",
                                "service", Map.of(
                                        "name", serviceName,
                                        "namespace", namespace,
                                        "port", 9443
                                )
                        )
                ))
        ));

        log.trace("Desired ConsolePlugin CR is {}", toYAML(r));
        return r;
    }
}
