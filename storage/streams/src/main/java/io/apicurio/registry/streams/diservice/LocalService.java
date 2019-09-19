package io.apicurio.registry.streams.diservice;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A holder of local streams service with accompanying serviceName
 *
 * @param <S> type of service
 */
public final class LocalService<S> {
    private String serviceName;
    private S service;

    public LocalService(String serviceName, S service) {
        this.serviceName = Objects.requireNonNull(serviceName);
        this.service = Objects.requireNonNull(service);
    }

    public String getServiceName() {
        return serviceName;
    }

    public S getService() {
        return service;
    }

    public static final class Registry<S> {
        private final Map<String, ? extends S> registry;

        public Registry(Collection<LocalService<? extends S>> localServices) {
            this.registry = localServices
                .stream()
                .collect(Collectors.toMap(LocalService::getServiceName, LocalService::getService));
        }

        public S get(String serviceName) {
            S service = registry.get(serviceName);
            if (service == null) {
                throw new IllegalStateException(
                    "No local service with name: " + serviceName + " registered");
            }
            return service;
        }
    }
}
