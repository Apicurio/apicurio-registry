package io.apicurio.registry.operator.updater;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3Spec;
import io.apicurio.registry.operator.api.v1.spec.ComponentSpec;
import io.apicurio.registry.operator.api.v1.spec.IngressSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

import static io.apicurio.registry.operator.utils.Utils.isBlank;
import static java.util.Optional.ofNullable;

public class IngressCRUpdater {

    private static final Logger log = LoggerFactory.getLogger(IngressCRUpdater.class);

    /**
     * @return true if the CR has been updated
     */
    public static boolean update(ApicurioRegistry3 primary) {
        var updatedApp = updateComponent(ofNullable(primary.getSpec()).map(ApicurioRegistry3Spec::getApp),
                "app");
        var updatedUi = updateComponent(ofNullable(primary.getSpec()).map(ApicurioRegistry3Spec::getUi),
                "ui");
        var updatedStudioUi = updateComponent(
                ofNullable(primary.getSpec()).map(ApicurioRegistry3Spec::getStudioUi), "studioUi");
        return updatedApp || updatedUi || updatedStudioUi;
    }

    private static boolean updateComponent(Optional<? extends ComponentSpec> component,
            String componentFieldName) {
        var host = component.map(ComponentSpec::getHost).filter(h -> !isBlank(h));
        var ingressHost = component.map(ComponentSpec::getIngress).map(IngressSpec::getHost)
                .filter(h -> !isBlank(h));
        if (host.isPresent()) {
            log.warn("CR field `{}.host` is DEPRECATED and should not be used.", componentFieldName);
            if (ingressHost.isEmpty()) {
                log.info("Performing automatic CR update from `{}.host` to `{}.ingress.host`.",
                        componentFieldName, componentFieldName);
                component.get().setHost(null);
                component.get().withIngress().setHost(host.get());
                return true;
            } else {
                log.warn(
                        "Automatic update cannot be performed, because the target field `{}.ingress.host` is already set.",
                        componentFieldName);
            }
        }
        return false;
    }
}
