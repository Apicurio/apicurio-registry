package io.apicurio.registry.operator.resource.app;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.feat.KubernetesOps;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.api.model.rbac.RoleBindingBuilder;
import io.fabric8.kubernetes.api.model.rbac.RoleRefBuilder;
import io.fabric8.kubernetes.api.model.rbac.SubjectBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static io.apicurio.registry.operator.utils.Mapper.toYAML;

@KubernetesDependent
public class AppRoleBindingResource
        extends CRUDKubernetesDependentResource<RoleBinding, ApicurioRegistry3> {

    private static final Logger log = LoggerFactory.getLogger(AppRoleBindingResource.class);

    public AppRoleBindingResource() {
        super(RoleBinding.class);
    }

    @Override
    protected RoleBinding desired(ApicurioRegistry3 primary, Context<ApicurioRegistry3> context) {
        var roleBinding = new RoleBindingBuilder()
                .withMetadata(new ObjectMetaBuilder()
                        .withName(KubernetesOps.getRoleBindingName(primary))
                        .withNamespace(KubernetesOps.getNamespace(primary))
                        .build())
                .withRoleRef(new RoleRefBuilder()
                        .withApiGroup("rbac.authorization.k8s.io")
                        .withKind("Role")
                        .withName(KubernetesOps.getRoleName(primary))
                        .build())
                .withSubjects(List.of(
                        new SubjectBuilder()
                                .withKind("ServiceAccount")
                                .withName(KubernetesOps.getServiceAccountName(primary))
                                .withNamespace(primary.getMetadata().getNamespace())
                                .build()))
                .build();

        log.trace("Desired AppRoleBindingResource is {}", toYAML(roleBinding));
        return roleBinding;
    }
}
