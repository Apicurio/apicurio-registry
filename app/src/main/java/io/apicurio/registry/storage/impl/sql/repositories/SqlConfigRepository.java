package io.apicurio.registry.storage.impl.sql.repositories;

import io.apicurio.common.apps.config.DynamicConfigPropertyDto;
import io.apicurio.registry.storage.error.RegistryStorageException;
import io.apicurio.registry.storage.impl.sql.HandleFactory;
import io.apicurio.registry.storage.impl.sql.SqlStatements;
import io.apicurio.registry.storage.impl.sql.mappers.DynamicConfigPropertyDtoMapper;
import io.apicurio.registry.utils.DtoUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static java.util.stream.Collectors.toList;

/**
 * Repository handling dynamic configuration property operations in the SQL storage layer.
 * Extracted from AbstractSqlRegistryStorage to improve maintainability.
 */
@ApplicationScoped
public class SqlConfigRepository {

    @Inject
    Logger log;

    @Inject
    SqlStatements sqlStatements;

    @Inject
    HandleFactory handles;

    /**
     * Set the HandleFactory to use for database operations.
     * This allows storage implementations to override the default injected HandleFactory.
     */
    public void setHandleFactory(HandleFactory handleFactory) {
        this.handles = handleFactory;
    }

    /**
     * Get all configuration properties.
     */
    public List<DynamicConfigPropertyDto> getConfigProperties() throws RegistryStorageException {
        log.debug("Getting all config properties.");
        return handles.withHandleNoException(handle -> {
            String sql = sqlStatements.selectConfigProperties();
            return handle.createQuery(sql)
                    .map(DynamicConfigPropertyDtoMapper.instance)
                    .list()
                    .stream()
                    // Filter out possible null values.
                    .filter(Objects::nonNull)
                    .collect(toList());
        });
    }

    /**
     * Get a configuration property by name.
     */
    public DynamicConfigPropertyDto getConfigProperty(String propertyName) throws RegistryStorageException {
        return getRawConfigProperty(propertyName);
    }

    /**
     * Get a raw configuration property by name (may return null if not found).
     */
    public DynamicConfigPropertyDto getRawConfigProperty(String propertyName) {
        log.debug("Selecting a single config property: {}", propertyName);
        return handles.withHandle(handle -> {
            final String normalizedPropertyName = DtoUtil.appAuthPropertyToRegistry(propertyName);
            Optional<DynamicConfigPropertyDto> res = handle
                    .createQuery(sqlStatements.selectConfigPropertyByName())
                    .bind(0, normalizedPropertyName)
                    .map(DynamicConfigPropertyDtoMapper.instance)
                    .findOne();
            return res.orElse(null);
        });
    }

    /**
     * Set a configuration property value.
     */
    public void setConfigProperty(DynamicConfigPropertyDto propertyDto) throws RegistryStorageException {
        log.debug("Setting a config property with name: {} and value: {}", propertyDto.getName(),
                propertyDto.getValue());
        handles.withHandleNoException(handle -> {
            String propertyName = propertyDto.getName();
            String propertyValue = propertyDto.getValue();

            // First delete the property row from the table
            handle.createUpdate(sqlStatements.deleteConfigProperty())
                    .bind(0, propertyName)
                    .execute();

            // Then create the row again with the new value
            handle.createUpdate(sqlStatements.insertConfigProperty())
                    .bind(0, propertyName)
                    .bind(1, propertyValue)
                    .bind(2, System.currentTimeMillis())
                    .execute();

            return null;
        });
    }

    /**
     * Delete a configuration property.
     */
    public void deleteConfigProperty(String propertyName) throws RegistryStorageException {
        handles.withHandle(handle -> {
            handle.createUpdate(sqlStatements.deleteConfigProperty())
                    .bind(0, propertyName)
                    .execute();
            return null;
        });
    }

    /**
     * Get all stale configuration properties (modified since last refresh).
     */
    public List<DynamicConfigPropertyDto> getStaleConfigProperties(Instant lastRefresh)
            throws RegistryStorageException {
        log.debug("Getting all stale config properties.");
        return handles.withHandleNoException(handle -> {
            return handle.createQuery(sqlStatements.selectStaleConfigProperties())
                    .bind(0, lastRefresh.toEpochMilli())
                    .map(DynamicConfigPropertyDtoMapper.instance)
                    .list()
                    .stream()
                    // Filter out possible null values.
                    .filter(Objects::nonNull)
                    .collect(toList());
        });
    }
}
