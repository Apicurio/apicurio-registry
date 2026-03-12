package io.apicurio.registry.flink;

import org.apache.flink.configuration.ConfigOption;
import org.apache.flink.configuration.ReadableConfig;
import org.apache.flink.table.catalog.Catalog;
import org.apache.flink.table.factories.CatalogFactory;
import org.apache.flink.table.factories.FactoryUtil;

import java.util.HashSet;
import java.util.Set;

import static io.apicurio.registry.flink.ApicurioCatalogOptions.AUTH_CLIENT_ID;
import static io.apicurio.registry.flink.ApicurioCatalogOptions.AUTH_CLIENT_SECRET;
import static io.apicurio.registry.flink.ApicurioCatalogOptions.AUTH_PASSWORD;
import static io.apicurio.registry.flink.ApicurioCatalogOptions.AUTH_TOKEN_ENDPOINT;
import static io.apicurio.registry.flink.ApicurioCatalogOptions.AUTH_TYPE;
import static io.apicurio.registry.flink.ApicurioCatalogOptions.AUTH_USERNAME;
import static io.apicurio.registry.flink.ApicurioCatalogOptions.CACHE_MAX_SIZE;
import static io.apicurio.registry.flink.ApicurioCatalogOptions.CACHE_TTL_MS;
import static io.apicurio.registry.flink.ApicurioCatalogOptions.DEFAULT_DATABASE;
import static io.apicurio.registry.flink.ApicurioCatalogOptions.IDENTIFIER;
import static io.apicurio.registry.flink.ApicurioCatalogOptions.REGISTRY_URL;

/**
 * Factory for creating ApicurioCatalog instances.
 */
public final class ApicurioCatalogFactory implements CatalogFactory {

    @Override
    public String factoryIdentifier() {
        return IDENTIFIER;
    }

    @Override
    public Set<ConfigOption<?>> requiredOptions() {
        final Set<ConfigOption<?>> options = new HashSet<>();
        options.add(REGISTRY_URL);
        return options;
    }

    @Override
    public Set<ConfigOption<?>> optionalOptions() {
        final Set<ConfigOption<?>> options = new HashSet<>();
        options.add(AUTH_TYPE);
        options.add(AUTH_USERNAME);
        options.add(AUTH_PASSWORD);
        options.add(AUTH_TOKEN_ENDPOINT);
        options.add(AUTH_CLIENT_ID);
        options.add(AUTH_CLIENT_SECRET);
        options.add(CACHE_TTL_MS);
        options.add(CACHE_MAX_SIZE);
        options.add(DEFAULT_DATABASE);
        return options;
    }

    @Override
    public Catalog createCatalog(final Context context) {
        final FactoryUtil.CatalogFactoryHelper helper = FactoryUtil.createCatalogFactoryHelper(
                this, context);
        helper.validate();

        final ReadableConfig cfg = helper.getOptions();
        final CatalogConfig config = CatalogConfig.builder()
                .name(context.getName())
                .defaultDatabase(cfg.get(DEFAULT_DATABASE))
                .url(cfg.get(REGISTRY_URL))
                .authType(cfg.get(AUTH_TYPE))
                .username(cfg.get(AUTH_USERNAME))
                .password(cfg.get(AUTH_PASSWORD))
                .tokenEndpoint(cfg.get(AUTH_TOKEN_ENDPOINT))
                .clientId(cfg.get(AUTH_CLIENT_ID))
                .clientSecret(cfg.get(AUTH_CLIENT_SECRET))
                .cacheTtlMs(cfg.get(CACHE_TTL_MS))
                .cacheMaxSize(cfg.get(CACHE_MAX_SIZE))
                .build();

        return new ApicurioCatalog(config);
    }
}
