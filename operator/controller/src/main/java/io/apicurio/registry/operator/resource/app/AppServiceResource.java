package io.apicurio.registry.operator.resource.app;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3Spec;
import io.apicurio.registry.operator.api.v1.spec.AppSpec;
import io.apicurio.registry.operator.feat.TLS;
import io.fabric8.kubernetes.api.model.IntOrStringBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

import static io.apicurio.registry.operator.resource.LabelDiscriminators.AppServiceDiscriminator;
import static io.apicurio.registry.operator.resource.ResourceKey.APP_SERVICE_KEY;
import static io.apicurio.registry.operator.utils.Mapper.toYAML;

@KubernetesDependent(resourceDiscriminator = AppServiceDiscriminator.class)
public class AppServiceResource extends CRUDKubernetesDependentResource<Service, ApicurioRegistry3> {

    private static final Logger log = LoggerFactory.getLogger(AppServiceResource.class);

    public AppServiceResource() {
        super(Service.class);
    }

    @Override
    protected Service desired(ApicurioRegistry3 primary, Context<ApicurioRegistry3> context) {
        var s = APP_SERVICE_KEY.getFactory().apply(primary);

        Optional.ofNullable(primary.getSpec())
                .map(ApicurioRegistry3Spec::getApp)
                .map(AppSpec::getTls)
                .ifPresent(tls -> {
                    var httpPort = new ServicePortBuilder()
                            .withName("http")
                            .withPort(8080)
                            .withTargetPort(new IntOrStringBuilder().withValue(8080).build())
                            .build();

                    var httpsPort = new ServicePortBuilder()
                            .withName("https")
                            .withPort(443)
                            .withTargetPort(new IntOrStringBuilder().withValue(8443).build())
                            .build();

                    if (TLS.insecureRequestsEnabled(tls)) {
                        s.getSpec().setPorts(List.of(httpsPort, httpPort));
                    }
                    else {
                        s.getSpec().setPorts(List.of(httpsPort));
                    }
                });

        log.trace("Desired {} is {}", APP_SERVICE_KEY.getId(), toYAML(s));
        return s;
    }
}
