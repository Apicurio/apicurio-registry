package io.apicurio.registry.systemtest.framework;

import io.apicurio.registry.operator.api.model.ApicurioRegistry;
import io.apicurio.registry.systemtest.registryinfra.ResourceManager;
import io.apicurio.registry.systemtest.registryinfra.resources.ApicurioRegistryResourceType;
import io.strimzi.api.kafka.model.Kafka;
import org.junit.jupiter.api.extension.ExtensionContext;

public class ApicurioRegistryUtils {
    public static void deployDefaultApicurioRegistryKafkasqlTLS(ExtensionContext testContext, Kafka kafka) {
        // Get Apicurio Registry
        ApicurioRegistry apicurioRegistryKafkasqlTLS = ApicurioRegistryResourceType.getDefaultKafkasql("rkubis-test-apicurio-registry-kafkasql-tls-instance", OperatorUtils.getStrimziOperatorNamespace());

        // Update to have TLS configuration
        ApicurioRegistryResourceType.updateWithDefaultTLS(apicurioRegistryKafkasqlTLS);

        // Create TLS certificates and secrets
        CertificateUtils.createCertificateStores(
                testContext,
                kafka.getMetadata().getName() + "-cluster-ca-cert",
                "apicurio-registry-kafka-user-secured-tls",
                apicurioRegistryKafkasqlTLS.getSpec().getConfiguration().getKafkasql().getSecurity().getTls().getTruststoreSecretName(),
                apicurioRegistryKafkasqlTLS.getSpec().getConfiguration().getKafkasql().getSecurity().getTls().getKeystoreSecretName(),
                kafka.getMetadata().getName() + "-kafka-bootstrap",
                kafka.getMetadata().getNamespace()
        );

        // Create Apicurio Registry with TLS configuration
        ResourceManager.getInstance().createResource(testContext, true, apicurioRegistryKafkasqlTLS);
    }

    public static void deployDefaultApicurioRegistryKafkasqlSCRAM(ExtensionContext testContext, Kafka kafka) {
        // Get Apicurio Registry
        ApicurioRegistry apicurioRegistryKafkasqlSCRAM = ApicurioRegistryResourceType.getDefaultKafkasql("rkubis-test-apicurio-registry-kafkasql-scram-instance", OperatorUtils.getStrimziOperatorNamespace());

        // Update to have SCRAM configuration
        ApicurioRegistryResourceType.updateWithDefaultSCRAM(apicurioRegistryKafkasqlSCRAM);

        // Create SCRAM certificates and secrets
        CertificateUtils.createCertificateStores(
                testContext,
                kafka.getMetadata().getName() + "-cluster-ca-cert",
                null,
                apicurioRegistryKafkasqlSCRAM.getSpec().getConfiguration().getKafkasql().getSecurity().getScram().getTruststoreSecretName(),
                null,
                null,
                kafka.getMetadata().getNamespace()
        );

        // Create Apicurio Registry with SCRAM configuration
        ResourceManager.getInstance().createResource(testContext, true, apicurioRegistryKafkasqlSCRAM);
    }
}
