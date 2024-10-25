package io.apicurio.registry.operator.feat;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;

import java.util.Map;

import static io.apicurio.registry.operator.resource.app.AppDeploymentResource.addEnvVar;

public class PostgresSql {

    public static final String ENV_APICURIO_STORAGE_KIND = "APICURIO_STORAGE_KIND";
    public static final String ENV_APICURIO_STORAGE_SQL_KIND = "APICURIO_STORAGE_SQL_KIND";
    public static final String ENV_APICURIO_DATASOURCE_URL = "APICURIO_DATASOURCE_URL";
    public static final String ENV_APICURIO_DATASOURCE_USERNAME = "APICURIO_DATASOURCE_USERNAME";
    public static final String ENV_APICURIO_DATASOURCE_PASSWORD = "APICURIO_DATASOURCE_PASSWORD";

    public static boolean configureDatasource(ApicurioRegistry3 primary, Map<String, EnvVar> env) {
        if (primary.getSpec().getApp().getSql() != null
                && primary.getSpec().getApp().getSql().getDatasource() != null) {
            var datasource = primary.getSpec().getApp().getSql().getDatasource();

            addEnvVar(env, new EnvVarBuilder().withName(ENV_APICURIO_STORAGE_KIND).withValue("sql").build());
            addEnvVar(env, new EnvVarBuilder().withName(ENV_APICURIO_STORAGE_SQL_KIND).withValue("postgresql")
                    .build());

            addEnvVar(env, new EnvVarBuilder().withName(ENV_APICURIO_DATASOURCE_URL)
                    .withValue(datasource.getUrl()).build());
            addEnvVar(env, new EnvVarBuilder().withName(ENV_APICURIO_DATASOURCE_USERNAME)
                    .withValue(datasource.getUsername()).build());
            addEnvVar(env, new EnvVarBuilder().withName(ENV_APICURIO_DATASOURCE_PASSWORD)
                    .withValue(datasource.getPassword()).build());
            return true;
        }
        return false;
    }
}
