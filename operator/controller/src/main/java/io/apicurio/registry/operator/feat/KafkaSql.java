package io.apicurio.registry.operator.feat;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3Spec;
import io.apicurio.registry.operator.api.v1.spec.AppSpec;
import io.apicurio.registry.operator.api.v1.spec.StorageSpec;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static io.apicurio.registry.operator.EnvironmentVariables.KAFKASQL_SECURITY_PROTOCOL;
import static io.apicurio.registry.operator.api.v1.ContainerNames.REGISTRY_APP_CONTAINER_NAME;
import static io.apicurio.registry.operator.resource.app.AppDeploymentResource.addEnvVar;
import static io.apicurio.registry.operator.utils.Utils.isBlank;
import static java.util.Optional.ofNullable;

public class KafkaSql {

    private static final Logger log = LoggerFactory.getLogger(KafkaSql.class);

    public static String ENV_STORAGE_KIND = "APICURIO_STORAGE_KIND";
    public static String ENV_KAFKASQL_BOOTSTRAP_SERVERS = "APICURIO_KAFKASQL_BOOTSTRAP_SERVERS";

    public static void configureKafkaSQL(ApicurioRegistry3 primary, Deployment deployment,
                                         Map<String, EnvVar> env) {
        ofNullable(primary.getSpec()).map(ApicurioRegistry3Spec::getApp).map(AppSpec::getStorage)
                .map(StorageSpec::getKafkasql).ifPresent(kafkasql -> {
                    if (!isBlank(kafkasql.getBootstrapServers())) {
                        addEnvVar(env,
                                new EnvVarBuilder().withName(ENV_STORAGE_KIND).withValue("kafkasql").build());
                        addEnvVar(env, new EnvVarBuilder().withName(ENV_KAFKASQL_BOOTSTRAP_SERVERS)
                                .withValue(kafkasql.getBootstrapServers()).build());

                        boolean sslConfigured = KafkaSqlTLS.configureKafkaSQLTLS(primary, deployment, REGISTRY_APP_CONTAINER_NAME,
                                env);

                        boolean oAuthConfigured = KafkaSqlAuth.configureKafkaSQLOauth(primary,
                                env);

                        if (sslConfigured) {
                            log.info("KafkaSQL storage with TLS security configured.");
                        }

                        if (oAuthConfigured) {
                            log.info("KafkaSQL storage with Oauth security configured.");
                        }

                        // Set the security protocol
                         if (sslConfigured) {
                            if (oAuthConfigured) {
                                addEnvVar(env, KAFKASQL_SECURITY_PROTOCOL, "SASL_SSL");
                            } else {
                                addEnvVar(env, KAFKASQL_SECURITY_PROTOCOL, "SSL");
                            }
                        } else if (oAuthConfigured) {
                            addEnvVar(env, KAFKASQL_SECURITY_PROTOCOL, "SASL_PLAINTEXT");
                        }
                    }
                });
    }
}
