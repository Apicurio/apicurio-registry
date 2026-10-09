package io.apicurio.registry.storage;

import io.apicurio.common.apps.config.DynamicConfigStorage;
import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.cdi.Raw;
import io.apicurio.registry.storage.decorator.RegistryStorageDecorator;
import io.apicurio.registry.storage.decorator.RegistryStorageProxyFactory;
import io.apicurio.registry.storage.impl.gitops.GitOpsRegistryStorage;
import io.apicurio.registry.storage.impl.kafkasql.KafkaSqlRegistryStorage;
import io.apicurio.registry.storage.impl.kubernetesops.KubernetesOpsRegistryStorage;
import io.apicurio.registry.storage.impl.sql.SqlRegistryStorage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_STORAGE;

@ApplicationScoped
public class RegistryStorageProducer {

    @Inject
    Logger log;

    @Inject
    Instance<RegistryStorageDecorator> decorators;

    @ConfigProperty(name = "apicurio.storage.kind", defaultValue = "sql")
    @Info(category = CATEGORY_STORAGE, description = "Application storage variant, for example, sql, kafkasql, gitops, or kubernetesops", availableSince = "3.0.0")
    String registryStorageType;

    private RegistryStorage cachedCurrent;

    private RegistryStorage cachedRaw;

    // Use Instance<> for lazy lookup to avoid instantiating beans that are not needed
    // based on the configured storage type. Combined with @LookupIfProperty on each
    // storage implementation, this ensures only the required storage beans are created.
    @Inject
    Instance<KafkaSqlRegistryStorage> kafkaSqlRegistryStorage;
    @Inject
    Instance<SqlRegistryStorage> sqlRegistryStorage;
    @Inject
    Instance<GitOpsRegistryStorage> gitOpsRegistryStorage;
    @Inject
    Instance<KubernetesOpsRegistryStorage> kubernetesOpsRegistryStorage;

    @Produces
    @ApplicationScoped
    @Current
    public RegistryStorage current() {
        if (cachedCurrent == null) {
            cachedCurrent = raw();

            Comparator<RegistryStorageDecorator> decoratorComparator = Comparator
                    .comparing(RegistryStorageDecorator::order);

            List<RegistryStorageDecorator> activeDecorators = decorators.stream()
                    .filter(RegistryStorageDecorator::isEnabled).sorted(decoratorComparator)
                    .collect(Collectors.toList());

            if (!activeDecorators.isEmpty()) {
                log.debug("Following RegistryStorage decorators have been enabled (in order): {}",
                        activeDecorators.stream().map(d -> d.getClass().getName())
                                .collect(Collectors.toList()));

                cachedCurrent = RegistryStorageProxyFactory.createProxy(cachedCurrent, activeDecorators);
            } else {
                log.debug("No RegistryStorage decorator has been enabled");
            }
        }

        return cachedCurrent;
    }

    @ConfigProperty(name = "apicurio.datasource.url", defaultValue = "unknown")
    String jdbcUrl;

    @ConfigProperty(name = "apicurio.kafkasql.bootstrap.servers", defaultValue = "unknown")
    String kafkaBootstrapServers;

    @Produces
    @ApplicationScoped
    @Raw
    public RegistryStorage raw() {
        if (cachedRaw == null) {
            if ("kafkasql".equals(registryStorageType)) {
                cachedRaw = kafkaSqlRegistryStorage.get();
            } else if ("gitops".equals(registryStorageType)) {
                cachedRaw = gitOpsRegistryStorage.get();
            } else if ("kubernetesops".equals(registryStorageType)) {
                cachedRaw = kubernetesOpsRegistryStorage.get();
            } else if ("sql".equals(registryStorageType)) {
                cachedRaw = sqlRegistryStorage.get();
            } else {
                throw new IllegalStateException(String
                        .format("No Registry storage variant defined for value %s", registryStorageType));
            }

            try {
                cachedRaw.initialize();
            } catch (Exception e) {
                if ("sql".equals(registryStorageType) && isSqlExceptionInCause(e)) {
                    log.debug("Database connection failure", e);
                    throw new RuntimeException(String.format(
                            "ERROR: PostgreSQL not reachable at %s. Check that the database is running and the connection URL is correct.",
                            jdbcUrl), e);
                }
                if ("kafkasql".equals(registryStorageType) && isTimeoutExceptionInCause(e)) {
                    log.debug("Kafka connection failure", e);
                    throw new RuntimeException(String.format(
                            "ERROR: Kafka not reachable at %s. Check that Kafka is running and the bootstrap servers are correct.",
                            kafkaBootstrapServers), e);
                }
                throw e;
            }

            log.info("Using the following RegistryStorage implementation: {}",
                    cachedRaw.getClass().getName());
        }
        return cachedRaw;
    }

    private boolean isSqlExceptionInCause(Throwable e) {
        Throwable current = e;
        while (current != null) {
            if (current instanceof java.sql.SQLException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isTimeoutExceptionInCause(Throwable e) {
        Throwable current = e;
        while (current != null) {
            if (current instanceof java.util.concurrent.TimeoutException || current.getClass().getSimpleName().contains("TimeoutException")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    @Produces
    @ApplicationScoped
    public DynamicConfigStorage configStorage() {
        return current();
    }
}
