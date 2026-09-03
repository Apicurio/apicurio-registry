package io.apicurio.registry.utils.tests;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.eclipse.microprofile.config.ConfigProvider;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class PostgreSqlEmbeddedTestResource implements QuarkusTestResourceLifecycleManager {

    private static final String STORAGE_SQL_KIND_KEY = "apicurio.storage.sql.kind";
    private static final String DB_KIND_POSTGRESQL = "postgresql";
    private static final String DB_USER_POSTGRES = "postgres";

    private EmbeddedPostgres database;

    /**
     * @see QuarkusTestResourceLifecycleManager#start()
     */
    @Override
    public Map<String, String> start() {
        if (!Boolean.parseBoolean(System.getProperty("cluster.tests")) && isPostgresqlStorage()) {

            String currentEnv = System.getenv("CURRENT_ENV");

            if (currentEnv != null && "mas".equals(currentEnv)) {
                Map<String, String> props = new HashMap<>();
                props.put(STORAGE_SQL_KIND_KEY, DB_KIND_POSTGRESQL);
                props.put("apicurio.datasource.url", "jdbc:postgresql://localhost:5432/test");
                props.put("apicurio.datasource.username", "test");
                props.put("apicurio.datasource.password", "test");
                return props;
            } else {
                return startPostgresql();
            }
        }
        return Collections.emptyMap();
    }

    private static boolean isPostgresqlStorage() {
        return ConfigProvider.getConfig().getValue(STORAGE_SQL_KIND_KEY, String.class)
                .equals(DB_KIND_POSTGRESQL);
    }

    private Map<String, String> startPostgresql() {
        try {
            database = EmbeddedPostgres.start();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        String datasourceUrl = database.getJdbcUrl(DB_USER_POSTGRES, DB_USER_POSTGRES);

        Map<String, String> props = new HashMap<>();
        props.put(STORAGE_SQL_KIND_KEY, DB_KIND_POSTGRESQL);
        props.put("apicurio.datasource.url", datasourceUrl);
        props.put("apicurio.datasource.username", DB_USER_POSTGRES);
        props.put("apicurio.datasource.password", DB_USER_POSTGRES);

        return props;
    }

    /**
     * @see QuarkusTestResourceLifecycleManager#stop()
     */
    @Override
    public void stop() {
        try {
            if (database != null) {
                database.close();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
