package io.apicurio.registry.operator.feat;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3Spec;
import io.apicurio.registry.operator.api.v1.spec.AppSpec;
import io.apicurio.registry.operator.api.v1.spec.KafkaSqlAuthSpec;
import io.apicurio.registry.operator.api.v1.spec.KafkaSqlSpec;
import io.apicurio.registry.operator.api.v1.spec.StorageSpec;
import io.apicurio.registry.operator.utils.SecretKeyRefTool;
import io.fabric8.kubernetes.api.model.EnvVar;

import java.util.Map;
import java.util.Optional;

import static io.apicurio.registry.operator.EnvironmentVariables.*;
import static io.apicurio.registry.operator.resource.app.AppDeploymentResource.addEnvVar;
import static java.util.Optional.ofNullable;

public class KafkaSqlAuth {

    /**
     * KafkaSQL must be already configured.
     */
    public static boolean configureKafkaSQLOauth(ApicurioRegistry3 primary, Map<String, EnvVar> env) {

        // spotless:off
        var clientSecret = new SecretKeyRefTool(getKafkaSqlAuthSpec(primary)
                .map(KafkaSqlAuthSpec::getClientSecretRef)
                .orElse(null), "clientSecret");

        var clientId = new SecretKeyRefTool(getKafkaSqlAuthSpec(primary)
                .map(KafkaSqlAuthSpec::getClientIdRef)
                .orElse(null), "clientId");

        if (clientSecret.isValid()) {
            getKafkaSqlAuthSpec(primary)
                    .filter(KafkaSqlAuthSpec::getEnabled)
                    .ifPresent(kafkaSqlAuthSpec -> {
                        addEnvVar(env, APICURIO_KAFKASQL_SECURITY_SASL_ENABLED, kafkaSqlAuthSpec.getEnabled().toString());
                        addEnvVar(env, APICURIO_KAFKASQL_SECURITY_SASL_MECHANISM, kafkaSqlAuthSpec.getMechanism());

                        clientId.applySecretEnvVar(env, APICURIO_KAFKASQL_SECURITY_SASL_CLIENT_ID);
                        clientSecret.applySecretEnvVar(env, APICURIO_KAFKASQL_SECURITY_SASL_CLIENT_SECRET);

                        addEnvVar(env, APICURIO_KAFKASQL_SECURITY_SASL_TOKEN_ENDPOINT, kafkaSqlAuthSpec.getTokenEndpoint());
                        addEnvVar(env, APICURIO_KAFKASQL_SECURITY_SASL_LOGIN_CALLBACK_HANDLER_CLASS, kafkaSqlAuthSpec.getLoginHandlerClass());
                    });

            return true;
        }
        return false;
    }

    private static Optional<KafkaSqlAuthSpec> getKafkaSqlAuthSpec(ApicurioRegistry3 primary) {
        // spotless:off
        return ofNullable(primary)
                .map(ApicurioRegistry3::getSpec)
                .map(ApicurioRegistry3Spec::getApp)
                .map(AppSpec::getStorage)
                .map(StorageSpec::getKafkasql)
                .map(KafkaSqlSpec::getAuth);
        // spotless:on
    }
}
