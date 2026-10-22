package io.apicurio.registry.operator.resource.app;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.feat.KubernetesOps;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.rbac.PolicyRuleBuilder;
import io.fabric8.kubernetes.api.model.rbac.Role;
import io.fabric8.kubernetes.api.model.rbac.RoleBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static io.apicurio.registry.operator.utils.Mapper.toYAML;

@KubernetesDependent
public class AppRoleResource extends CRUDKubernetesDependentResource<Role, ApicurioRegistry3> {

    private static final Logger log = LoggerFactory.getLogger(AppRoleResource.class);

    public AppRoleResource() {
        super(Role.class);
    }

    @Override
    protected Role desired(ApicurioRegistry3 primary, Context<ApicurioRegistry3> context) {
        var role = new RoleBuilder()
                .withMetadata(new ObjectMetaBuilder()
                        .withName(KubernetesOps.getRoleName(primary))
                        .withNamespace(KubernetesOps.getNamespace(primary))
                        .build())
                .withRules(List.of(
                        new PolicyRuleBuilder()
                                .withApiGroups("")
                                .withResources("configmaps")
                                .withVerbs("get", "list", "watch")
                                .build()))
                .build();

        log.trace("Desired AppRoleResource is {}", toYAML(role));
        return role;
    }
}
