package io.apicurio.registry.storage.decorator;

/**
 * Decorators are ordered by natural int ordering, e.g. one with a lower order value is executed first.
 */
public interface RegistryStorageDecoratorOrderConstants {
    int READ_ONLY_DECORATOR = 10;
    int KAFKA_SQL_DECORATOR = 20;
    int LIMITS_ENFORCER_DECORATOR = 30;
    int CONFIG_CACHE_DECORATOR = 40;
    int EVENT_SOURCED_DECORATOR = 50;
}
