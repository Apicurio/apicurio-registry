package io.apicurio.registry.operator.utils;

import io.apicurio.registry.operator.Configuration;
import io.apicurio.registry.operator.OperatorException;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3Spec;
import io.apicurio.registry.operator.api.v1.spec.AppSpec;
import io.apicurio.registry.operator.api.v1.spec.IngressSpec;
import io.apicurio.registry.operator.api.v1.spec.UiSpec;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.networking.v1.HTTPIngressPath;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressRule;
import io.fabric8.kubernetes.api.model.networking.v1.IngressTLS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_APP;
import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_UI;
import static io.apicurio.registry.operator.utils.Utils.isBlank;
import static java.util.Optional.ofNullable;

public final class IngressUtils {

    private static final Logger log = LoggerFactory.getLogger(IngressUtils.class);

    private IngressUtils() {
    }

    /**
     * Get the host configured in the ingress. If no host is configured in the ingress then a null is
     * returned.
     * 
     * @param component
     * @param primary
     */
    public static String getConfiguredHost(String component, ApicurioRegistry3 primary) {
        String host = switch (component) {
            case COMPONENT_APP -> ofNullable(primary.getSpec()).map(ApicurioRegistry3Spec::getApp)
                    .map(AppSpec::getIngress).map(IngressSpec::getHost).filter(h -> !isBlank(h)).orElse(null);
            case COMPONENT_UI -> ofNullable(primary.getSpec()).map(ApicurioRegistry3Spec::getUi)
                    .map(UiSpec::getIngress).map(IngressSpec::getHost).filter(h -> !isBlank(h)).orElse(null);
            default -> throw new OperatorException("Unexpected value: " + component);
        };
        return host;
    }

    /**
     * Get the host for an ingress. If not configured, a default value is returned.
     * 
     * @param component
     * @param primary
     */
    public static String getHost(String component, ApicurioRegistry3 primary) {
        String host = getConfiguredHost(component, primary);
        if (host == null) {
            // TODO: This is not used because of the current activation conditions.
            host = "%s-%s.%s%s".formatted(primary.getMetadata().getName(), component,
                    primary.getMetadata().getNamespace(), Configuration.getDefaultBaseHost());
        }
        log.trace("Host for component {} is {}", component, host);
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

    /**
     * Get the TLS hosts and secrets for an ingress. If not configured, an empty list is returned.
     *
     * @param component
     * @param primary
     */
    public static List<IngressTLS> getTls(String component, ApicurioRegistry3 primary) {
        Map<String, List<String>> tlsSecrets = switch (component) {
            case COMPONENT_APP -> ofNullable(primary.getSpec()).map(ApicurioRegistry3Spec::getApp)
                    .map(AppSpec::getIngress).map(IngressSpec::getTlsSecrets).orElseGet(null);
            case COMPONENT_UI -> ofNullable(primary.getSpec()).map(ApicurioRegistry3Spec::getUi)
                    .map(UiSpec::getIngress).map(IngressSpec::getTlsSecrets).orElseGet(null);
            default -> throw new OperatorException("Unexpected value: " + component);
        };

        List<IngressTLS> tlsList = new ArrayList<>();

        if (tlsSecrets != null && !tlsSecrets.isEmpty()) {
            for (Map.Entry<String, List<String>> entry : tlsSecrets.entrySet()) {
                String secretName = entry.getKey();
                List<String> hosts = entry.getValue();
                if (!Utils.isBlank(secretName) && hosts != null && !hosts.isEmpty()) {
                    List<String> validHosts = hosts.stream()
                            .filter(host -> !Utils.isBlank(host))
                            .toList();
                    if (!validHosts.isEmpty()) {
                        IngressTLS tls = new IngressTLS();
                        tls.setHosts(validHosts);
                        tls.setSecretName(secretName);
                        tlsList.add(tls);
                    }
                }
            }
        }
        log.trace("TLS list for component {} is {}", component, tlsList);
        return tlsList;
    }
}
