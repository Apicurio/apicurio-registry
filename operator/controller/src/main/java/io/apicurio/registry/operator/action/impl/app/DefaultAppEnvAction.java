package io.apicurio.registry.operator.action.impl.app;

import io.apicurio.registry.operator.action.AbstractBasicAction;
import io.apicurio.registry.operator.context.CRContext;
import io.apicurio.registry.operator.resource.ResourceKey;
import io.apicurio.registry.operator.state.NoState;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;

import java.util.ArrayList;
import java.util.List;

import static io.apicurio.registry.operator.resource.ResourceFactory.APP_CONTAINER_NAME;
import static io.apicurio.registry.operator.resource.ResourceKey.APP_DEPLOYMENT_KEY;
import static io.apicurio.registry.operator.utils.TraverseUtils.where;

public class DefaultAppEnvAction extends AbstractBasicAction {

    @Override
    public List<ResourceKey<?>> supports() {
        return List.of(APP_DEPLOYMENT_KEY);
    }

    @Override
    public void run(NoState state, CRContext crContext) {

        var appEnv = new ArrayList<>(List.of(
                // spotless:off
                new EnvVarBuilder().withName("QUARKUS_PROFILE").withValue("prod").build(),
                new EnvVarBuilder().withName("APICURIO_CONFIG_CACHE_ENABLED").withValue("true").build(),
                new EnvVarBuilder().withName("QUARKUS_HTTP_ACCESS_LOG_ENABLED").withValue("true").build(),
                new EnvVarBuilder().withName("QUARKUS_HTTP_CORS_ORIGINS").withValue("*").build(),
                new EnvVarBuilder().withName("APICURIO_REST_DELETION_GROUP_ENABLED").withValue("true").build(),
                new EnvVarBuilder().withName("APICURIO_REST_DELETION_ARTIFACT_ENABLED").withValue("true").build(),
                new EnvVarBuilder().withName("APICURIO_REST_DELETION_ARTIFACTVERSION_ENABLED").withValue("true").build(),
                new EnvVarBuilder().withName("APICURIO_APIS_V2_DATE_FORMAT").withValue("yyyy-MM-dd''T''HH:mm:ssZ").build()
                // spotless:on
        ));

        // spotless:off
        crContext.withDesiredResource(APP_DEPLOYMENT_KEY, d -> {
            where(d.getSpec().getTemplate().getSpec().getContainers(), c -> APP_CONTAINER_NAME.equals(c.getName()), c -> {
                c.setEnv(appEnv);
            });
        });
        // spotless:on
    }
}
