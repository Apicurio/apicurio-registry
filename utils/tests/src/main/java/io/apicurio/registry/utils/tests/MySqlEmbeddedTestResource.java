package io.apicurio.registry.utils.tests;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.eclipse.microprofile.config.ConfigProvider;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class MySqlEmbeddedTestResource implements QuarkusTestResourceLifecycleManager {

    private static final String DB_PASSWORD = "P4ssw0rd!#";

    private static final DockerImageName IMAGE = DockerImageName.parse("mysql:latest");
    private MySQLContainer<?> database = new MySQLContainer<>(IMAGE).withPassword(DB_PASSWORD);

    /**
     * Constructor.
     */
    public MySqlEmbeddedTestResource() {
        System.out.println("[MySqlEmbeddedTestResource] MySQL test resource constructed.");
    }

    /**
     * @see QuarkusTestResourceLifecycleManager#start()
     */
    @Override
    public Map<String, String> start() {
        if (!Boolean.parseBoolean(System.getProperty("cluster.tests")) && isMySqlStorage()) {

            System.out.println("[MySqlEmbeddedTestResource] MySQL test resource starting.");

            String currentEnv = System.getenv("CURRENT_ENV");

            if ("mas".equals(currentEnv)) {
                Map<String, String> props = new HashMap<>();
                props.put("apicurio.storage.sql.kind", "mysql");
                props.put("apicurio.datasource.url", "jdbc:mysql://localhost:3306/test");
                props.put("apicurio.datasource.username", "test");
                props.put("apicurio.datasource.password", "test");
                return props;
            } else {
                return startMySql();
            }
        }
        return Collections.emptyMap();
    }

    private static boolean isMySqlStorage() {
        return ConfigProvider.getConfig().getValue("apicurio.storage.sql.kind", String.class).equals("mysql");
    }

    private Map<String, String> startMySql() {
        database.start();

        String datasourceUrl = database.getJdbcUrl();

        Map<String, String> props = new HashMap<>();
        props.put("apicurio.storage.sql.kind", "mysql");
        props.put("apicurio.datasource.url", datasourceUrl);
        props.put("apicurio.datasource.username", "root");
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
        System.out.println("[MySqlEmbeddedTestResource] MySQL test resource stopped.");
    }
}