package io.apicurio.registry.operator.action.impl.ui;

import io.apicurio.registry.operator.action.AbstractBasicAction;
import io.apicurio.registry.operator.context.CRContext;
import io.apicurio.registry.operator.resource.ResourceKey;
import io.apicurio.registry.operator.state.NoState;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;

import java.util.ArrayList;
import java.util.List;

import static io.apicurio.registry.operator.resource.ResourceFactory.UI_CONTAINER_NAME;
import static io.apicurio.registry.operator.resource.ResourceKey.*;
import static io.apicurio.registry.operator.utils.IngressUtils.withIngressRule;
import static io.apicurio.registry.operator.utils.TraverseUtils.where;

public class DefaultUIEnvAction extends AbstractBasicAction {

    @Override
    public List<ResourceKey<?>> supports() {
        return List.of(UI_DEPLOYMENT_KEY);
    }

    @Override
    public void run(NoState state, CRContext crContext) {

        crContext.withExistingResource(APP_SERVICE_KEY, appS -> {
            crContext.withExistingResource(APP_INGRESS_KEY, appI -> {
                withIngressRule(appS, appI, rule -> {

                    var uiEnv = new ArrayList<EnvVar>();

                    // spotless:off
                    uiEnv.add(new EnvVarBuilder()
                            .withName("REGISTRY_API_URL")
                            .withValue("http://%s/apis/registry/v3".formatted(rule.getHost()))
                            .build());

                    crContext.withDesiredResource(UI_DEPLOYMENT_KEY, d -> {
                        where(d.getSpec().getTemplate().getSpec().getContainers(), c -> UI_CONTAINER_NAME.equals(c.getName()), c -> {
                            c.setEnv(uiEnv);
                        });
                    });
                    // spotless:on
                });
            });
        });
    }
}
