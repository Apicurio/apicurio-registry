package io.apicurio.registry.operator.feat;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3Spec;
import io.apicurio.registry.operator.api.v1.spec.AppSpec;
import io.apicurio.registry.operator.api.v1.spec.SqlSpec;
import io.apicurio.registry.operator.api.v1.spec.StorageSpec;
import io.apicurio.registry.operator.utils.SecretKeyRefTool;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;

import java.util.Map;

import static io.apicurio.registry.operator.resource.app.AppDeploymentResource.addEnvVar;
import static java.util.Optional.ofNullable;

public class PostgresSql {

    public static final String ENV_APICURIO_STORAGE_KIND = "APICURIO_STORAGE_KIND";
    public static final String ENV_APICURIO_STORAGE_SQL_KIND = "APICURIO_STORAGE_SQL_KIND";
    public static final String ENV_APICURIO_DATASOURCE_URL = "APICURIO_DATASOURCE_URL";
    public static final String ENV_APICURIO_DATASOURCE_USERNAME = "APICURIO_DATASOURCE_USERNAME";
    public static final String ENV_APICURIO_DATASOURCE_PASSWORD = "APICURIO_DATASOURCE_PASSWORD";

    public static void configureDatasource(ApicurioRegistry3 primary, Map<String, EnvVar> env) {
        ofNullable(primary.getSpec()).map(ApicurioRegistry3Spec::getApp).map(AppSpec::getStorage)
                .map(StorageSpec::getSql).map(SqlSpec::getDataSource).ifPresent(dataSource -> {

                    // TODO: Validation
                    addEnvVar(env,
                            new EnvVarBuilder().withName(ENV_APICURIO_STORAGE_KIND).withValue("sql").build());
                    addEnvVar(env, new EnvVarBuilder().withName(ENV_APICURIO_STORAGE_SQL_KIND)
                            .withValue("postgresql").build());

                    addEnvVar(env, new EnvVarBuilder().withName(ENV_APICURIO_DATASOURCE_URL)
                            .withValue(dataSource.getUrl()).build());
                    addEnvVar(env, new EnvVarBuilder().withName(ENV_APICURIO_DATASOURCE_USERNAME)
                            .withValue(dataSource.getUsername()).build());

                    var password = new SecretKeyRefTool(dataSource.getPassword(), "password");
                    if (password.isValid()) {
                        password.applySecretEnvVar(env, ENV_APICURIO_DATASOURCE_PASSWORD);
                    }
                });
    }
}
