package io.apicurio.registry.operator.action.impl;

import io.apicurio.registry.operator.action.AbstractBasicAction;
import io.apicurio.registry.operator.action.ActionOrder;
import io.apicurio.registry.operator.api.v3.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v3.v1.AppSpec;
import io.apicurio.registry.operator.api.v3.v1.UISpec;
import io.apicurio.registry.operator.context.CRContext;
import io.apicurio.registry.operator.state.NoState;
import io.apicurio.registry.operator.state.impl.ClusterInfo;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import static io.apicurio.registry.operator.action.ActionOrder.ORDERING_EARLY;
import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_APP;
import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_UI;
import static io.apicurio.registry.operator.resource.ResourceKey.REGISTRY_KEY;
import static io.apicurio.registry.operator.utils.TraverseUtils.isEmpty;

@ApplicationScoped
public class HostInitAction extends AbstractBasicAction {

    @Inject
    ClusterInfo clusterInfo;

    @Override
    public ActionOrder ordering() {
        return ORDERING_EARLY;
    }

    @Override
    public void run(NoState state, CRContext crContext) {

        crContext.withDesiredResource(REGISTRY_KEY, p -> {

            if (p.getSpec().getApp() == null) {
                p.getSpec().setApp(new AppSpec());
            }

            if (isEmpty(p.getSpec().getApp().getHost()) && clusterInfo.getCanonicalHost() != null) {
                p.getSpec().getApp().setHost(getHost(COMPONENT_APP, p));
            }

            if (p.getSpec().getUi() == null) {
                p.getSpec().setUi(new UISpec());
            }

            if (isEmpty(p.getSpec().getUi().getHost()) && clusterInfo.getCanonicalHost() != null) {
                p.getSpec().getUi().setHost(getHost(COMPONENT_UI, p));
            }
        });
    }

    private String getHost(String component, ApicurioRegistry3 p) {
        var prefix = p.getMetadata().getName() + "-" + component + "." + p.getMetadata().getNamespace();
        String host;
        if (clusterInfo.getCanonicalHost().isPresent()) {
            host = prefix + "." + clusterInfo.getCanonicalHost().get();
        } else {
            host = prefix + ".cluster.example";
        }
        return host;
    }
}
