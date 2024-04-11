package io.apicurio.registry.utils.tests;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.eclipse.microprofile.config.ConfigProvider;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class MsSqlEmbeddedTestResource implements QuarkusTestResourceLifecycleManager {
    
    private static final String DB_PASSWORD = "P4ssw0rd!#";

    private static final DockerImageName IMAGE = DockerImageName.parse("mcr.microsoft.com/mssql/server").withTag("2022-latest");
    private MSSQLServerContainer<?> database = new MSSQLServerContainer<>(IMAGE)
            .withPassword(DB_PASSWORD)
            .acceptLicense();
    
    /**
     * Constructor.
     */
    public MsSqlEmbeddedTestResource() {
        System.out.println("[MsSqlEmbeddedTestResource] MS SQLServer test resource constructed.");
    }

    /**
     * @see QuarkusTestResourceLifecycleManager#start()
     */
    @Override
    public Map<String, String> start() {
        if (!Boolean.parseBoolean(System.getProperty("cluster.tests")) && isMssqlStorage()) {

            System.out.println("[MsSqlEmbeddedTestResource] MS SQLServer test resource starting.");

            String currentEnv = System.getenv("CURRENT_ENV");

            if ("mas".equals(currentEnv)) {
                Map<String, String> props = new HashMap<>();
                props.put("apicurio.storage.db-kind", "mssql");
                props.put("apicurio.datasource.url", "jdbc:sqlserver://mssql;");
                props.put("apicurio.datasource.username", "test");
                props.put("apicurio.datasource.password", "test");
                return props;
            } else {
                return startMsSql();
            }
        }
        return Collections.emptyMap();
    }

    private static boolean isMssqlStorage() {
        return ConfigProvider.getConfig().getValue("apicurio.storage.db-kind", String.class).equals("mssql");
    }

    private Map<String, String> startMsSql() {
        database.start();

        String datasourceUrl = database.getJdbcUrl();

        Map<String, String> props = new HashMap<>();
        props.put("apicurio.storage.db-kind", "mssql");
        props.put("apicurio.datasource.url", datasourceUrl);
        props.put("apicurio.datasource.username", "SA");
        props.put("apicurio.datasource.password", DB_PASSWORD);
        return props;
    }

    /**
     * @see QuarkusTestResourceLifecycleManager#stop()
     */
    @Override
    public void stop() {
        if (database != null) {
            database.close();
        }
        System.out.println("[MsSqlEmbeddedTestResource] MS SQLServer test resource stopped.");
    }

}
