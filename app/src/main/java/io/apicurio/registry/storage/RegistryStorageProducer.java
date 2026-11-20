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
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptor;
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

    // The storage whose initialize() is running: beans it touches while initializing get it back,
    // instead of starting a second initialization.
    private RegistryStorage initializing;

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

    @Produces
    @ApplicationScoped
    @Raw
    public RegistryStorage raw() {
        if (cachedRaw == null) {
            if (initializing != null) {
                return initializing;
            }
            RegistryStorage storage;
            if ("kafkasql".equals(registryStorageType)) {
                storage = kafkaSqlRegistryStorage.get();
            } else if ("gitops".equals(registryStorageType)) {
                storage = gitOpsRegistryStorage.get();
            } else if ("kubernetesops".equals(registryStorageType)) {
                storage = kubernetesOpsRegistryStorage.get();
            } else if ("sql".equals(registryStorageType)) {
                storage = sqlRegistryStorage.get();
            } else {
                throw new IllegalStateException(String
                        .format("No Registry storage variant defined for value %s", registryStorageType));
            }

            // Cached only once initialized, so a failed initialization is never handed out later.
            initializing = storage;
            try {
                storage.initialize();
            } finally {
                initializing = null;
            }
            cachedRaw = storage;
            log.info("Using the following RegistryStorage implementation: {}",
                    cachedRaw.getClass().getName());
        }
        return cachedRaw;
    }

    /**
     * Initializes the storage at startup, right after the experimental features gate, so a storage
     * that cannot be used (e.g. an unusable KafkaSQL journal topic) always stops the application,
     * whichever bean would otherwise have touched it first.
     */
    @Startup(Interceptor.Priority.PLATFORM_BEFORE + 1)
    void initializeStorage() {
        try {
            raw();
        } catch (RuntimeException e) {
            log.error("The '{}' storage could not be initialized, stopping the application: {}",
                    registryStorageType, e.getMessage());
            throw e;
        }
    }

    @Produces
    @ApplicationScoped
    public DynamicConfigStorage configStorage() {
        return current();
    }
}
