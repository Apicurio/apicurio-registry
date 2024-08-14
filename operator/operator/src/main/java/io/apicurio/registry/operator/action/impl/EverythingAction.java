package io.apicurio.registry.operator.action.impl;

import io.apicurio.registry.operator.action.AbstractBasicAction;
import io.apicurio.registry.operator.api.v3.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.context.CRContext;
import io.apicurio.registry.operator.resource.ResourceKey;
import io.apicurio.registry.operator.state.NoState;
import io.apicurio.registry.operator.state.impl.ClusterInfo;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.HTTPIngressPath;
import io.fabric8.kubernetes.api.model.networking.v1.IngressRule;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;

import static io.apicurio.registry.operator.resource.ResourceFactory.*;
import static io.apicurio.registry.operator.resource.ResourceKey.*;
import static io.apicurio.registry.operator.utils.TraverseUtils.where;

/**
 * Create a basic runnable Registry deployment
 */
@ApplicationScoped
public class EverythingAction extends AbstractBasicAction {

    @Inject
    ClusterInfo clusterInfo;

    @Override
    public List<ResourceKey<?>> supports() {
        return List.of(POSTGRESQL_DEPLOYMENT_KEY, APP_DEPLOYMENT_KEY, UI_DEPLOYMENT_KEY,
                POSTGRESQL_SERVICE_KEY, APP_SERVICE_KEY, UI_SERVICE_KEY, APP_INGRESS_KEY, UI_INGRESS_KEY);
    }

    @Override
    public void run(NoState state, CRContext crContext) {

        // App

        crContext.withDesiredResource(APP_DEPLOYMENT_KEY, d -> {

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

            crContext.withExistingResource(POSTGRESQL_SERVICE_KEY, s -> {
                appEnv.addAll(List.of(
                        // spotless:off
                        new EnvVarBuilder().withName("APICURIO_STORAGE_KIND").withValue("sql").build(),
                        new EnvVarBuilder().withName("APICURIO_STORAGE_SQL_KIND").withValue("postgresql").build(),
                        new EnvVarBuilder().withName("APICURIO_DATASOURCE_USERNAME").withValue("apicurio-registry").build(),
                        new EnvVarBuilder().withName("APICURIO_DATASOURCE_PASSWORD").withValue("password").build(),
                        new EnvVarBuilder().withName("APICURIO_DATASOURCE_URL").withValue(
                                "jdbc:postgresql://%s.%s.svc.cluster.local:5432/apicurio-registry"
                                        .formatted(s.getMetadata().getName(), s.getMetadata().getNamespace())
                                ).build()));
                        // spotless:on
            });

            where(d.getSpec().getTemplate().getSpec().getContainers(),
                    c -> APP_CONTAINER_NAME.equals(c.getName()), c -> {
                        c.setEnv(appEnv);
                    });
        });

        // UI

        var uiEnv = new ArrayList<EnvVar>();

        crContext.withExistingResource(APP_SERVICE_KEY, s -> {
            crContext.withExistingResource(APP_INGRESS_KEY, i -> {
                for (IngressRule rule : i.getSpec().getRules()) {
                    for (HTTPIngressPath path : rule.getHttp().getPaths()) {
                        if (s.getMetadata().getName().equals(path.getBackend().getService().getName())) {
                            uiEnv.add(new EnvVarBuilder().withName("REGISTRY_API_URL")
                                    .withValue("http://%s/apis/registry/v3".formatted(rule.getHost()))
                                    .build());
                            return;
                        }
                    }
                }
            });
        });

        crContext.withDesiredResource(UI_DEPLOYMENT_KEY, d -> {
            where(d.getSpec().getTemplate().getSpec().getContainers(),
                    c -> UI_CONTAINER_NAME.equals(c.getName()), c -> {
                        c.setEnv(uiEnv);
                    });
        });

        // Host

        if (clusterInfo.getCanonicalHost() != null) {

            crContext.withDesiredResource(REGISTRY_KEY, p -> {

                crContext.withExistingResource(APP_SERVICE_KEY, s -> {
                    crContext.withDesiredResource(APP_INGRESS_KEY, i -> {
                        for (IngressRule rule : i.getSpec().getRules()) {
                            for (HTTPIngressPath path : rule.getHttp().getPaths()) {
                                if (s.getMetadata().getName()
                                        .equals(path.getBackend().getService().getName())) {
                                    rule.setHost(getHost(COMPONENT_APP, p));
                                    return;
                                }
                            }
                        }
                    });
                });

                crContext.withExistingResource(UI_SERVICE_KEY, s -> {
                    crContext.withDesiredResource(UI_INGRESS_KEY, i -> {
                        for (IngressRule rule : i.getSpec().getRules()) {
                            for (HTTPIngressPath path : rule.getHttp().getPaths()) {
                                if (s.getMetadata().getName()
                                        .equals(path.getBackend().getService().getName())) {
                                    rule.setHost(getHost(COMPONENT_UI, p));
                                    return;
                                }
                            }
                        }
                    });
                });
            });
        }
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
