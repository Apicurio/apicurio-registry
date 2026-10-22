package io.apicurio.registry.operator.resource.app;

import io.apicurio.registry.operator.Configuration;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.feat.GitOps;
import io.apicurio.registry.operator.resource.Labels;
import io.fabric8.kubernetes.api.model.IntOrStringBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_APP;
import static io.apicurio.registry.operator.utils.Mapper.toYAML;

@KubernetesDependent
public class GitOpsSshServiceResource
        extends CRUDKubernetesDependentResource<Service, ApicurioRegistry3> {

    private static final Logger log = LoggerFactory.getLogger(GitOpsSshServiceResource.class);

    public GitOpsSshServiceResource() {
        super(Service.class);
    }

    @Override
    protected Service desired(ApicurioRegistry3 primary, Context<ApicurioRegistry3> context) {
        var sshPort = new ServicePortBuilder()
                .withName(GitOps.SSH_PORT_NAME)
                .withPort(GitOps.SSH_PORT)
                .withTargetPort(new IntOrStringBuilder().withValue(GitOps.SSH_PORT).build())
                .withProtocol("TCP")
                .build();

        var labels = new HashMap<>(Map.of(
                "app", primary.getMetadata().getName(),
                "app.kubernetes.io/name", "apicurio-registry",
                "app.kubernetes.io/component", "gitops-ssh",
                "app.kubernetes.io/instance", primary.getMetadata().getName(),
                "app.kubernetes.io/version", Configuration.getRegistryVersion(),
                "app.kubernetes.io/part-of", "apicurio-registry",
                "app.kubernetes.io/managed-by", "apicurio-registry-operator"
        ));

        var s = new ServiceBuilder()
                .withNewMetadata()
                .withName(GitOps.getSshServiceName(primary))
                .withNamespace(primary.getMetadata().getNamespace())
                .withLabels(labels)
                .endMetadata()
                .withNewSpec()
                .withPorts(List.of(sshPort))
                .withSelector(Labels.getSelectorLabels(primary, COMPONENT_APP))
                .withPublishNotReadyAddresses(true)
                .endSpec()
                .build();

        log.trace("Desired GitOpsSshServiceResource is {}", toYAML(s));
        return s;
    }
}
