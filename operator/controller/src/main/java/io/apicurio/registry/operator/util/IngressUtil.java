package io.apicurio.registry.operator.util;

import io.apicurio.registry.operator.OperatorException;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.networking.v1.HTTPIngressPath;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressRule;
import org.eclipse.microprofile.config.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_APP;
import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_UI;
import static io.apicurio.registry.operator.util.Util.isBlank;

public class IngressUtil {

    private static final Logger log = LoggerFactory.getLogger(IngressUtil.class);

    private IngressUtil() {
    }

    public static String getHost(String component, ApicurioRegistry3 p) {
        String host = null;
        if (COMPONENT_APP.equals(component) && !isBlank(p.getSpec().getAppHost())) {
            host = p.getSpec().getAppHost();
        } else if (COMPONENT_UI.equals(component) && !isBlank(p.getSpec().getUiHost())) {
            host = p.getSpec().getUiHost();
        } else {
            throw new OperatorException("Unexpected value: " + component);
        }
        if (host == null) {
            var defaultBaseHost = ConfigProvider.getConfig()
                    .getOptionalValue("apicurio.operator.default-base-host", String.class)
                    .orElse("cluster.example");
            host = "%s-%s.%s.%s".formatted(p.getMetadata().getName(), component,
                    p.getMetadata().getNamespace(), defaultBaseHost);
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
