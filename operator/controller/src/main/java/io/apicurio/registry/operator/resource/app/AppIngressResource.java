package io.apicurio.registry.operator.resource.app;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.utils.Utils;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressTLS;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

import static io.apicurio.registry.operator.AnnotationManager.updateResourceAnnotations;
import static io.apicurio.registry.operator.CRContext.getCRContext;
import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_APP;
import static io.apicurio.registry.operator.resource.ResourceKey.APP_INGRESS_KEY;
import static io.apicurio.registry.operator.resource.ResourceKey.APP_SERVICE_KEY;
import static io.apicurio.registry.operator.utils.IngressUtils.*;
import static io.apicurio.registry.operator.utils.Mapper.toYAML;
import static io.apicurio.registry.operator.utils.Utils.isBlank;
import static io.apicurio.registry.operator.utils.Utils.updateResourceManually;
import static io.apicurio.registry.utils.Cell.cell;

@KubernetesDependent
public class AppIngressResource extends CRUDKubernetesDependentResource<Ingress, ApicurioRegistry3> {

    private static final Logger log = LoggerFactory.getLogger(AppIngressResource.class);

    public AppIngressResource() {
        super(Ingress.class);
    }

    @Override
    protected Ingress desired(ApicurioRegistry3 primary, Context<ApicurioRegistry3> context) {
        var i = APP_INGRESS_KEY.getFactory().apply(primary);

        var sOpt = Utils.getSecondaryResource(context, primary, APP_SERVICE_KEY);
        sOpt.ifPresent(s -> withIngressRule(s, i, rule -> rule.setHost(getHost(COMPONENT_APP, primary))));

        List<IngressTLS> tlsList = getTls(COMPONENT_APP, primary);

        if (!tlsList.isEmpty()) {
            i.getSpec().setTls(tlsList);
        }

        // Standard approach does not work properly :(
        updateResourceAnnotations(context, i, getCRContext(primary).getAppIngressAnnotations(), primary.withSpec().withApp().withIngress().getAnnotations());

        var desired = cell(primary.withSpec().withApp().withIngress().getIngressClassName());
        if (isBlank(desired.get())) {
            desired.set(null);
        }
        i.getSpec().setIngressClassName(desired.get());
        // Standard approach does not work properly :(
        updateResourceManually(context, i, r -> {
            var actual = r.getSpec().getIngressClassName();
            if (!Objects.equals(actual, desired.get())) {
                r.getSpec().setIngressClassName(desired.get());
                return true;
            }
            return false;
        });

        log.trace("Desired {} is:\n\n{}\n\n", APP_INGRESS_KEY.getId(), toYAML(i));
        return i;
    }
}
