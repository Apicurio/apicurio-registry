package io.apicurio.registry.operator.feat;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static io.apicurio.registry.operator.resource.ResourceFactory.APP_CONTAINER_NAME;
import static io.apicurio.registry.operator.resource.app.AppDeploymentResource.addEnvVar;
import static io.apicurio.registry.operator.utils.Utils.isBlank;

public class KafkaSQL {

    private static final Logger log = LoggerFactory.getLogger(KafkaSQL.class);

    public static String ENV_STORAGE_KIND = "APICURIO_STORAGE_KIND";
    public static String ENV_KAFKASQL_BOOTSTRAP_SERVERS = "APICURIO_KAFKASQL_BOOTSTRAP_SERVERS";

    public static boolean configureKafkaSQL(ApicurioRegistry3 primary, Deployment deployment,
            Map<String, EnvVar> env) {
        if (primary.getSpec().getApp().getKafkasql() != null
                && !isBlank(primary.getSpec().getApp().getKafkasql().getBootstrapServers())) {

            addEnvVar(env, ENV_STORAGE_KIND, "kafkasql");
            addEnvVar(env, ENV_KAFKASQL_BOOTSTRAP_SERVERS,
                    primary.getSpec().getApp().getKafkasql().getBootstrapServers());

            if (KafkaSQLTLS.configureKafkaSQLTLS(primary, deployment, APP_CONTAINER_NAME, env)) {
                log.info("KafkaSQL storage with TLS security configured.");
            }

            return true;
        }
        return false;
    }
}
