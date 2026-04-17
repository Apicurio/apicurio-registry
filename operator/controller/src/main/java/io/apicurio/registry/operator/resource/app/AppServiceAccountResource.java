package io.apicurio.registry.operator.resource.app;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.feat.KubernetesOps;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.ServiceAccountBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.apicurio.registry.operator.utils.Mapper.toYAML;

@KubernetesDependent
public class AppServiceAccountResource
        extends CRUDKubernetesDependentResource<ServiceAccount, ApicurioRegistry3> {

    private static final Logger log = LoggerFactory.getLogger(AppServiceAccountResource.class);

    public AppServiceAccountResource() {
        super(ServiceAccount.class);
    }

    @Override
    protected ServiceAccount desired(ApicurioRegistry3 primary, Context<ApicurioRegistry3> context) {
        var sa = new ServiceAccountBuilder()
                .withMetadata(new ObjectMetaBuilder()
                        .withName(KubernetesOps.getServiceAccountName(primary))
                        .withNamespace(primary.getMetadata().getNamespace())
                        .build())
                .build();

        log.trace("Desired AppServiceAccountResource is {}", toYAML(sa));
        return sa;
    }
}
