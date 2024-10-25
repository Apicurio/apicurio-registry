package io.apicurio.registry.operator.feat;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;

import java.util.Map;

import static io.apicurio.registry.operator.resource.app.AppDeploymentResource.addEnvVar;
import static io.apicurio.registry.operator.utils.Utils.isBlank;

public class KafkaSql {

    public static String ENV_STORAGE_KIND = "APICURIO_STORAGE_KIND";
    public static String ENV_KAFKASQL_BOOTSTRAP_SERVERS = "APICURIO_KAFKASQL_BOOTSTRAP_SERVERS";

    public static boolean configureKafkaSQL(ApicurioRegistry3 primary, Map<String, EnvVar> env) {
        if (primary.getSpec().getApp().getKafkasql() != null
                && !isBlank(primary.getSpec().getApp().getKafkasql().getBootstrapServers())) {
            addEnvVar(env, new EnvVarBuilder().withName(ENV_STORAGE_KIND).withValue("kafkasql").build());
            addEnvVar(env, new EnvVarBuilder().withName(ENV_KAFKASQL_BOOTSTRAP_SERVERS)
                    .withValue(primary.getSpec().getApp().getKafkasql().getBootstrapServers()).build());
            return true;
        }
        return false;
    }
}
