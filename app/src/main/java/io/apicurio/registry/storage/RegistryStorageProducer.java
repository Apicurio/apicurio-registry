package io.apicurio.registry.storage;

import io.apicurio.common.apps.config.DynamicConfigStorage;
import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.storage.decorator.RegistryStorageDecorator;
import io.apicurio.registry.storage.impl.gitops.GitOpsRegistryStorage;
import io.apicurio.registry.storage.impl.kafkasql.KafkaSqlRegistryStorage;
import io.apicurio.registry.storage.impl.sql.SqlRegistryStorage;
import io.apicurio.registry.types.Current;
import io.apicurio.registry.types.Raw;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class RegistryStorageProducer {

    @Inject
    Logger log;

    @Inject
    Instance<RegistryStorageDecorator> decorators;

    @ConfigProperty(name = "registry.storage.kind")
    @Info
    String registryStorageType;

    private RegistryStorage cachedCurrent;

    private RegistryStorage cachedRaw;

    @Inject
    KafkaSqlRegistryStorage kafkaSqlRegistryStorage;
    @Inject
    SqlRegistryStorage sqlRegistryStorage;
    @Inject
    GitOpsRegistryStorage gitOpsRegistryStorage;

    @Produces
    @ApplicationScoped
    @Current
    public RegistryStorage current() {
        if (cachedCurrent == null) {
            cachedCurrent = raw();

            Comparator<RegistryStorageDecorator> decoratorComparator = Comparator
                    .comparing(RegistryStorageDecorator::order);

            List<RegistryStorageDecorator> activeDecorators = decorators.stream()
                    .filter(RegistryStorageDecorator::isEnabled)
                    .sorted(decoratorComparator)
                    .collect(Collectors.toList());

            if (!activeDecorators.isEmpty()) {
                log.debug("Following RegistryStorage decorators have been enabled (in order): {}",
                        activeDecorators.stream().map(d -> d.getClass().getName()).collect(Collectors.toList()));

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
                cachedRaw = kafkaSqlRegistryStorage;
            } else if ("gitops".equals(registryStorageType)) {
                cachedRaw = gitOpsRegistryStorage;
            } else if ("sql".equals(registryStorageType)) {
                cachedRaw = sqlRegistryStorage;
            } else {
                throw new IllegalStateException(String.format("No Registry storage variant defined for value %s", registryStorageType));
            }

            cachedRaw.initialize();
            log.info("Using the following RegistryStorage implementation: {}", cachedRaw.getClass().getName());
        }
        return cachedRaw;
    }


    @Produces
    @ApplicationScoped
    public DynamicConfigStorage configStorage() {
        return current();
    }
}
