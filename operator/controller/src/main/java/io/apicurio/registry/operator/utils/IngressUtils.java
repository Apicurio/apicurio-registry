package io.apicurio.registry.operator.utils;

import io.apicurio.registry.operator.Configuration;
import io.apicurio.registry.operator.OperatorException;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3Spec;
import io.apicurio.registry.operator.api.v1.spec.AppSpec;
import io.apicurio.registry.operator.api.v1.spec.IngressSpec;
import io.apicurio.registry.operator.api.v1.spec.StudioUiSpec;
import io.apicurio.registry.operator.api.v1.spec.UiSpec;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.networking.v1.HTTPIngressPath;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

import static io.apicurio.registry.operator.resource.ResourceFactory.*;
import static io.apicurio.registry.operator.utils.Utils.isBlank;
import static java.util.Optional.ofNullable;

public final class IngressUtils {

    private static final Logger log = LoggerFactory.getLogger(IngressUtils.class);

    private IngressUtils() {
    }

    public static String getHost(String component, ApicurioRegistry3 p) {
        String host = switch (component) {
            case COMPONENT_APP -> ofNullable(p.getSpec()).map(ApicurioRegistry3Spec::getApp)
                    .map(AppSpec::getIngress).map(IngressSpec::getHost).filter(h -> !isBlank(h)).orElse(null);
            case COMPONENT_UI -> ofNullable(p.getSpec()).map(ApicurioRegistry3Spec::getUi)
                    .map(UiSpec::getIngress).map(IngressSpec::getHost).filter(h -> !isBlank(h)).orElse(null);
            case COMPONENT_STUDIO_UI ->
                ofNullable(p.getSpec()).map(ApicurioRegistry3Spec::getStudioUi).map(StudioUiSpec::getIngress)
                        .map(IngressSpec::getHost).filter(h -> !isBlank(h)).orElse(null);
            default -> throw new OperatorException("Unexpected value: " + component);
        };
        if (host == null) {
            // TODO: This is not used because of the current activation conditions.
            host = "%s-%s.%s%s".formatted(p.getMetadata().getName(), component,
                    p.getMetadata().getNamespace(), Configuration.getDefaultBaseHost());
        }
        log.debug("Host for component {} is {}", component, host);
        return host;
    }

    public static void withIngressRule(Service s, Ingress i, Consumer<IngressRule> action) {
        for (IngressRule rule : i.getSpec().getRules()) {
            for (HTTPIngressPath path : rule.getHttp().getPaths()) {
                if (s.getMetadata().getName().equals(path.getBackend().getService().getName())) {
                    action.accept(rule);
                    return;
                }
            }
        }
    }
}
