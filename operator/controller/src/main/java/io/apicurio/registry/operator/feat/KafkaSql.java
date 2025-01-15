package io.apicurio.registry.operator.feat;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3Spec;
import io.apicurio.registry.operator.api.v1.spec.AppSpec;
import io.apicurio.registry.operator.api.v1.spec.StorageSpec;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;

import java.util.Map;

import static io.apicurio.registry.operator.resource.app.AppDeploymentResource.addEnvVar;
import static io.apicurio.registry.operator.utils.Utils.isBlank;
import static java.util.Optional.ofNullable;

public class KafkaSql {

    public static String ENV_STORAGE_KIND = "APICURIO_STORAGE_KIND";
    public static String ENV_KAFKASQL_BOOTSTRAP_SERVERS = "APICURIO_KAFKASQL_BOOTSTRAP_SERVERS";

    public static void configureKafkaSQL(ApicurioRegistry3 primary, Map<String, EnvVar> env) {
        ofNullable(primary.getSpec()).map(ApicurioRegistry3Spec::getApp).map(AppSpec::getStorage)
                .map(StorageSpec::getKafkasql).ifPresent(kafkasql -> {
                    if (!isBlank(kafkasql.getBootstrapServers())) {
                        addEnvVar(env,
                                new EnvVarBuilder().withName(ENV_STORAGE_KIND).withValue("kafkasql").build());
                        addEnvVar(env, new EnvVarBuilder().withName(ENV_KAFKASQL_BOOTSTRAP_SERVERS)
                                .withValue(kafkasql.getBootstrapServers()).build());
                    }
                });
    }
}
