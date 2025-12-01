package io.apicurio.registry.operator.feat;

import io.apicurio.registry.operator.OperatorException;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3Spec;
import io.apicurio.registry.operator.api.v1.spec.AppSpec;
import io.apicurio.registry.operator.api.v1.spec.SqlSpec;
import io.apicurio.registry.operator.api.v1.spec.StorageType;
import io.apicurio.registry.operator.utils.SecretKeyRefTool;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;

import java.util.Map;

import static io.apicurio.registry.operator.resource.app.AppDeploymentResource.addEnvVar;
import static java.util.Optional.ofNullable;

/**
 * Configures SQL storage for Apicurio Registry, supporting both PostgreSQL and MySQL databases.
 */
public class SqlStorage {

    public static final String ENV_APICURIO_STORAGE_KIND = "APICURIO_STORAGE_KIND";
    public static final String ENV_APICURIO_STORAGE_SQL_KIND = "APICURIO_STORAGE_SQL_KIND";
    public static final String ENV_APICURIO_DATASOURCE_URL = "APICURIO_DATASOURCE_URL";
    public static final String ENV_APICURIO_DATASOURCE_USERNAME = "APICURIO_DATASOURCE_USERNAME";
    public static final String ENV_APICURIO_DATASOURCE_PASSWORD = "APICURIO_DATASOURCE_PASSWORD";

    /**
     * Configures the datasource environment variables for SQL storage based on the storage type
     * (PostgreSQL or MySQL).
     *
     * @param primary the ApicurioRegistry3 custom resource
     * @param env the environment variables map to populate
     * @throws OperatorException if the JDBC URL does not match the declared storage type
     */
    public static void configureDatasource(ApicurioRegistry3 primary, Map<String, EnvVar> env) {
        ofNullable(primary.getSpec()).map(ApicurioRegistry3Spec::getApp).map(AppSpec::getStorage)
                .ifPresent(storage -> {
                    var storageType = storage.getType();
                    ofNullable(storage.getSql()).map(SqlSpec::getDataSource).ifPresent(dataSource -> {

                        // Validate that the JDBC URL matches the storage type
                        var url = dataSource.getUrl();
                        if (url != null) {
                            validateJdbcUrl(storageType, url);
                        }

                        // Configure storage kind
                        addEnvVar(env,
                                new EnvVarBuilder().withName(ENV_APICURIO_STORAGE_KIND).withValue("sql").build());

                        // Set SQL kind based on storage type
                        String sqlKind = getSqlKind(storageType);
                        addEnvVar(env, new EnvVarBuilder().withName(ENV_APICURIO_STORAGE_SQL_KIND)
                                .withValue(sqlKind).build());

                        // Configure datasource connection details
                        addEnvVar(env, new EnvVarBuilder().withName(ENV_APICURIO_DATASOURCE_URL)
                                .withValue(url).build());
                        addEnvVar(env, new EnvVarBuilder().withName(ENV_APICURIO_DATASOURCE_USERNAME)
                                .withValue(dataSource.getUsername()).build());

                        var password = new SecretKeyRefTool(dataSource.getPassword(), "password");
                        if (password.isValid()) {
                            password.applySecretEnvVar(env, ENV_APICURIO_DATASOURCE_PASSWORD);
                        }
                    });
                });
    }

    /**
     * Determines the SQL kind value based on the storage type.
     *
     * @param storageType the storage type from the CR
     * @return "postgresql" or "mysql" depending on the storage type
     * @throws OperatorException if the storage type is not a supported SQL type
     */
    private static String getSqlKind(StorageType storageType) {
        if (storageType == null) {
            throw new OperatorException("Storage type must be specified for SQL storage");
        }
        return switch (storageType) {
            case POSTGRESQL -> "postgresql";
            case MYSQL -> "mysql";
            default -> throw new OperatorException(
                    "Unsupported SQL storage type: " + storageType.getValue());
        };
    }

    /**
     * Validates that the JDBC URL matches the declared storage type.
     *
     * @param storageType the storage type from the CR
     * @param jdbcUrl the JDBC URL from the datasource configuration
     * @throws OperatorException if the URL does not match the storage type
     */
    private static void validateJdbcUrl(StorageType storageType, String jdbcUrl) {
        if (storageType == null || jdbcUrl == null) {
            return;
        }

        String expectedPrefix = switch (storageType) {
            case POSTGRESQL -> "jdbc:postgresql://";
            case MYSQL -> "jdbc:mysql://";
            default -> null;
        };

        if (expectedPrefix != null && !jdbcUrl.startsWith(expectedPrefix)) {
            throw new OperatorException(String.format(
                    "JDBC URL mismatch: storage type is '%s' but JDBC URL is '%s'. "
                            + "Expected URL to start with '%s'",
                    storageType.getValue(), jdbcUrl, expectedPrefix));
        }
    }
}
