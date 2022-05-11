package io.apicurio.registry.systemtest.framework;

import io.apicurio.registry.operator.api.model.ApicurioRegistry;
import io.apicurio.registry.operator.api.model.ApicurioRegistrySpecConfigurationKafkaSecurity;
import io.apicurio.registry.systemtest.registryinfra.ResourceManager;
import io.apicurio.registry.systemtest.registryinfra.resources.ApicurioRegistryResourceType;
import io.strimzi.api.kafka.model.Kafka;
import org.junit.jupiter.api.extension.ExtensionContext;

public class ApicurioRegistryUtils {
    private static String getTruststoreSecretName(ApicurioRegistry registry) {
        ApicurioRegistrySpecConfigurationKafkaSecurity security = registry
                .getSpec()
                .getConfiguration()
                .getKafkasql()
                .getSecurity();

        if (security.getTls() != null) {
            return security.getTls().getTruststoreSecretName();
        } else if (security.getScram() != null) {
            return security.getScram().getTruststoreSecretName();
        }

        return null;
    }

    private static String getKeystoreSecretName(ApicurioRegistry registry) {
        ApicurioRegistrySpecConfigurationKafkaSecurity security = registry
                .getSpec()
                .getConfiguration()
                .getKafkasql()
                .getSecurity();

        if (security.getTls() != null) {
            return security.getTls().getKeystoreSecretName();
        }

        return null;
    }

    public static void deployDefaultApicurioRegistryKafkasqlNoAuth(ExtensionContext testContext) {
        // Get Apicurio Registry
        ApicurioRegistry apicurioRegistryKafkasqlNoAuth = ApicurioRegistryResourceType.getDefaultKafkasql(
                Constants.REGISTRY_NAME,
                Environment.KAFKA_NAMESPACE
        );

        // Create Apicurio Registry without authentication
        ResourceManager.getInstance().createResource(testContext, true, apicurioRegistryKafkasqlNoAuth);
    }

    public static void deployDefaultApicurioRegistryKafkasqlTLS(ExtensionContext testContext, Kafka kafka) {
        // Get Apicurio Registry
        ApicurioRegistry apicurioRegistryKafkasqlTLS = ApicurioRegistryResourceType.getDefaultKafkasql(
                Constants.REGISTRY_NAME,
                Environment.KAFKA_NAMESPACE
        );

        // Update Apicurio Registry to have TLS configuration
        ApicurioRegistryResourceType.updateWithDefaultTLS(apicurioRegistryKafkasqlTLS);

        CertificateUtils.createTruststore(
                testContext,
                kafka.getMetadata().getNamespace(),
                kafka.getMetadata().getName() + "-cluster-ca-cert",
                getTruststoreSecretName(apicurioRegistryKafkasqlTLS)
        );

        CertificateUtils.createKeystore(
                testContext,
                kafka.getMetadata().getNamespace(),
                Constants.KAFKA_USER,
                getKeystoreSecretName(apicurioRegistryKafkasqlTLS),
                kafka.getMetadata().getName() + "-kafka-bootstrap"
        );

        // Create Apicurio Registry with TLS configuration
        ResourceManager.getInstance().createResource(testContext, true, apicurioRegistryKafkasqlTLS);
    }

    public static void deployDefaultApicurioRegistryKafkasqlSCRAM(ExtensionContext testContext, Kafka kafka) {
        // Get Apicurio Registry
        ApicurioRegistry apicurioRegistryKafkasqlSCRAM = ApicurioRegistryResourceType.getDefaultKafkasql(
                Constants.REGISTRY_NAME,
                Environment.KAFKA_NAMESPACE
        );

        // Update to have SCRAM configuration
        ApicurioRegistryResourceType.updateWithDefaultSCRAM(apicurioRegistryKafkasqlSCRAM);

        CertificateUtils.createTruststore(
                testContext,
                kafka.getMetadata().getNamespace(),
                kafka.getMetadata().getName() + "-cluster-ca-cert",
                getTruststoreSecretName(apicurioRegistryKafkasqlSCRAM)
        );

        // Create Apicurio Registry with SCRAM configuration
        ResourceManager.getInstance().createResource(testContext, true, apicurioRegistryKafkasqlSCRAM);
    }
}
