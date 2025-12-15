package io.apicurio.registry.storage;

import io.apicurio.common.apps.config.DynamicConfigStorage;
import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.storage.decorator.RegistryStorageDecorator;
import io.apicurio.registry.storage.impl.gitops.GitOpsRegistryStorage;
import io.apicurio.registry.storage.impl.kafkasql.KafkaSqlRegistryStorage;
import io.apicurio.registry.storage.impl.sql.SqlRegistryStorage;
import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.cdi.Raw;
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

    @ConfigProperty(name = "apicurio.storage.kind")
    @Info(category = CATEGORY_STORAGE, description = "Application storage variant, for example, sql, kafkasql, or gitops", availableSince = "3.0.0")
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

                for (int i = activeDecorators.size() - 1; i >= 0; i--) {
                    RegistryStorageDecorator decorator = activeDecorators.get(i);
                    decorator.setDelegate(cachedCurrent);
                    cachedCurrent = decorator;
                }
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
            if ("kafkasql".equals(registryStorageType)) {
                cachedRaw = kafkaSqlRegistryStorage.get();
            } else if ("gitops".equals(registryStorageType)) {
                cachedRaw = gitOpsRegistryStorage.get();
            } else if ("sql".equals(registryStorageType)) {
                cachedRaw = sqlRegistryStorage.get();
            } else {
                throw new IllegalStateException(String
                        .format("No Registry storage variant defined for value %s", registryStorageType));
            }

            cachedRaw.initialize();
            log.info("Using the following RegistryStorage implementation: {}",
                    cachedRaw.getClass().getName());
        }
        return cachedRaw;
    }

    @Produces
    @ApplicationScoped
    public DynamicConfigStorage configStorage() {
        return current();
    }
}
