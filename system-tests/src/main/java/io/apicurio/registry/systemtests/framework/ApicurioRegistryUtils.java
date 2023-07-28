package io.apicurio.registry.systemtests.framework;

import io.apicur.registry.v1.ApicurioRegistry;
import io.apicur.registry.v1.apicurioregistryspec.configuration.kafkasql.Security;
import io.apicurio.registry.systemtests.platform.Kubernetes;
import io.apicurio.registry.systemtests.registryinfra.ResourceManager;
import io.apicurio.registry.systemtests.registryinfra.resources.ApicurioRegistryResourceType;
import io.apicurio.registry.systemtests.time.TimeoutBudget;
import io.fabric8.openshift.api.model.Route;
import io.strimzi.api.kafka.model.Kafka;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;

import java.text.MessageFormat;
import java.time.Duration;

public class ApicurioRegistryUtils {
    private static final Logger LOGGER = LoggerUtils.getLogger();

    private static String getTruststoreSecretName(ApicurioRegistry registry) {
        Security security = registry
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
        Security security = registry
                .getSpec()
                .getConfiguration()
                .getKafkasql()
                .getSecurity();

        if (security.getTls() != null) {
            return security.getTls().getKeystoreSecretName();
        }

        return null;
    }

    public static ApicurioRegistry deployDefaultApicurioRegistrySql(
            ExtensionContext testContext,
            boolean useKeycloak
    ) throws InterruptedException {
        // Get Apicurio Registry
        ApicurioRegistry apicurioRegistrySql = ApicurioRegistryResourceType.getDefaultSql(
                Constants.REGISTRY,
                Environment.NAMESPACE
        );

        if (useKeycloak) {
            ApicurioRegistryResourceType.updateWithDefaultKeycloak(apicurioRegistrySql);
        }

        // Create Apicurio Registry
        ResourceManager.getInstance().createResource(true, apicurioRegistrySql);

        return apicurioRegistrySql;
    }

    public static ApicurioRegistry deployDefaultApicurioRegistryKafkasqlNoAuth(
            ExtensionContext testContext,
            boolean useKeycloak
    ) throws InterruptedException {
        // Get Apicurio Registry
        ApicurioRegistry apicurioRegistryKafkasqlNoAuth = ApicurioRegistryResourceType.getDefaultKafkasql(
                Constants.REGISTRY,
                Environment.NAMESPACE
        );

        if (useKeycloak) {
            ApicurioRegistryResourceType.updateWithDefaultKeycloak(apicurioRegistryKafkasqlNoAuth);
        }

        // Create Apicurio Registry without authentication
        ResourceManager.getInstance().createResource(true, apicurioRegistryKafkasqlNoAuth);

        return apicurioRegistryKafkasqlNoAuth;
    }

    public static ApicurioRegistry deployDefaultApicurioRegistryKafkasqlTLS(
            ExtensionContext testContext,
            Kafka kafka,
            boolean useKeycloak
    ) throws InterruptedException {
        // Get Apicurio Registry
        ApicurioRegistry apicurioRegistryKafkasqlTLS = ApicurioRegistryResourceType.getDefaultKafkasql(
                Constants.REGISTRY,
                Environment.NAMESPACE
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

        if (useKeycloak) {
            ApicurioRegistryResourceType.updateWithDefaultKeycloak(apicurioRegistryKafkasqlTLS);
        }

        // Create Apicurio Registry with TLS configuration
        ResourceManager.getInstance().createResource(true, apicurioRegistryKafkasqlTLS);

        return apicurioRegistryKafkasqlTLS;
    }

    public static ApicurioRegistry deployDefaultApicurioRegistryKafkasqlSCRAM(
            ExtensionContext testContext,
            Kafka kafka,
            boolean useKeycloak
    ) throws InterruptedException {
        // Get Apicurio Registry
        ApicurioRegistry apicurioRegistryKafkasqlSCRAM = ApicurioRegistryResourceType.getDefaultKafkasql(
                Constants.REGISTRY,
                Environment.NAMESPACE
        );

        // Update to have SCRAM configuration
        ApicurioRegistryResourceType.updateWithDefaultSCRAM(apicurioRegistryKafkasqlSCRAM);

        CertificateUtils.createTruststore(
                testContext,
                kafka.getMetadata().getNamespace(),
                kafka.getMetadata().getName() + "-cluster-ca-cert",
                getTruststoreSecretName(apicurioRegistryKafkasqlSCRAM)
        );

        if (useKeycloak) {
            ApicurioRegistryResourceType.updateWithDefaultKeycloak(apicurioRegistryKafkasqlSCRAM);
        }

        // Create Apicurio Registry with SCRAM configuration
        ResourceManager.getInstance().createResource(true, apicurioRegistryKafkasqlSCRAM);

        return apicurioRegistryKafkasqlSCRAM;
    }

    public static boolean waitApicurioRegistryHostnameReady(ApicurioRegistry apicurioRegistry) {
        String name = apicurioRegistry.getMetadata().getName();
        String namespace = apicurioRegistry.getMetadata().getNamespace();
        String info = MessageFormat.format("with name {0} in namespace {1}", name, namespace);
        TimeoutBudget timeout = TimeoutBudget.ofDuration(Duration.ofMinutes(3));

        LOGGER.info("Waiting for hostname of ApicurioRegistry {} to be ready...", info);

        while (!timeout.timeoutExpired()) {
            if (isApicurioRegistryHostnameReady(apicurioRegistry)) {
                return true;
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();

                return false;
            }
        }

        if (!isApicurioRegistryHostnameReady(apicurioRegistry)) {
            LOGGER.error("Hostname of ApicurioRegistry {} failed readiness check.", info);

            return false;
        }

        return true;
    }

    public static String getApicurioRegistryHostname(ApicurioRegistry apicurioRegistry) {
        Route route = Kubernetes.getRoute(apicurioRegistry);

        if (route == null) {
            return null;
        }

        return Kubernetes.getRouteHost(route.getMetadata().getNamespace(), route.getMetadata().getName());
    }

    public static boolean isApicurioRegistryHostnameReady(ApicurioRegistry apicurioRegistry) {
        // Apicurio Registry values
        String registryName = apicurioRegistry.getMetadata().getName();
        String registryNamespace = apicurioRegistry.getMetadata().getNamespace();
        String defaultRegistryHostname = registryName + "." + registryNamespace;

        // Get Route
        Route registryRoute = Kubernetes.getRoute(apicurioRegistry);

        if (registryRoute == null) {
            return false;
        }

        String registryRouteNamespace = registryRoute.getMetadata().getNamespace();
        String registryRouteName = registryRoute.getMetadata().getName();

        return (
                Kubernetes.isRouteReady(registryRouteNamespace, registryRouteName)
                && !defaultRegistryHostname.equals(Kubernetes.getRouteHost(registryRouteNamespace, registryRouteName))
        );
    }

    public static boolean waitApicurioRegistryReady(ApicurioRegistry apicurioRegistry) {
        ApicurioRegistryResourceType registryResourceType = new ApicurioRegistryResourceType();
        TimeoutBudget timeoutBudget = TimeoutBudget.ofDuration(registryResourceType.getTimeout());

        while (!timeoutBudget.timeoutExpired()) {
            if (registryResourceType.isReady(apicurioRegistry)) {
                return true;
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();

                return false;
            }
        }

        return registryResourceType.isReady(apicurioRegistry);
    }
}
