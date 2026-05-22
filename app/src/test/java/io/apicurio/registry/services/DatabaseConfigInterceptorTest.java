package io.apicurio.registry.services;

import io.apicurio.registry.storage.impl.sql.RegistryDatabaseKind;
import io.smallrye.config.ConfigSourceInterceptorContext;
import io.smallrye.config.ConfigValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DatabaseConfigInterceptor}.
 */
public class DatabaseConfigInterceptorTest {

    private DatabaseConfigInterceptor interceptor;
    private ConfigSourceInterceptorContext context;

    @BeforeEach
    void setUp() {
        interceptor = new DatabaseConfigInterceptor();
        context = mock(ConfigSourceInterceptorContext.class);
    }

    // ---- Active properties ----

    @Test
    void testH2ActiveWhenH2Selected() {
        setupStorageKind("h2");
        ConfigValue result = interceptor.getValue(context, "quarkus.datasource.h2.active");
        assertEquals("true", result.getValue());
    }

    @Test
    void testH2InactiveWhenPostgresqlSelected() {
        setupStorageKind("postgresql");
        ConfigValue result = interceptor.getValue(context, "quarkus.datasource.h2.active");
        assertEquals("false", result.getValue());
    }

    @Test
    void testPostgresqlActiveWhenPostgresqlSelected() {
        setupStorageKind("postgresql");
        ConfigValue result = interceptor.getValue(context, "quarkus.datasource.postgresql.active");
        assertEquals("true", result.getValue());
    }

    @Test
    void testMysqlActiveWhenMysqlSelected() {
        setupStorageKind("mysql");
        ConfigValue result = interceptor.getValue(context, "quarkus.datasource.mysql.active");
        assertEquals("true", result.getValue());
    }

    @Test
    void testMssqlActiveWhenMssqlSelected() {
        setupStorageKind("mssql");
        ConfigValue result = interceptor.getValue(context, "quarkus.datasource.mssql.active");
        assertEquals("true", result.getValue());
    }

    // ---- Telemetry properties ----

    @Test
    void testH2TelemetryEnabledWhenH2Selected() {
        setupStorageKind("h2");
        ConfigValue result = interceptor.getValue(context, "quarkus.datasource.h2.jdbc.telemetry");
        assertEquals("true", result.getValue());
    }

    @Test
    void testH2TelemetryDisabledWhenPostgresqlSelected() {
        setupStorageKind("postgresql");
        ConfigValue result = interceptor.getValue(context, "quarkus.datasource.h2.jdbc.telemetry");
        assertEquals("false", result.getValue());
    }

    @Test
    void testPostgresqlTelemetryEnabledWhenPostgresqlSelected() {
        setupStorageKind("postgresql");
        ConfigValue result = interceptor.getValue(context, "quarkus.datasource.postgresql.jdbc.telemetry");
        assertEquals("true", result.getValue());
    }

    @Test
    void testPostgresqlTelemetryDisabledWhenH2Selected() {
        setupStorageKind("h2");
        ConfigValue result = interceptor.getValue(context, "quarkus.datasource.postgresql.jdbc.telemetry");
        assertEquals("false", result.getValue());
    }

    @Test
    void testMysqlTelemetryEnabledWhenMysqlSelected() {
        setupStorageKind("mysql");
        ConfigValue result = interceptor.getValue(context, "quarkus.datasource.mysql.jdbc.telemetry");
        assertEquals("true", result.getValue());
    }

    @Test
    void testMssqlTelemetryEnabledWhenMssqlSelected() {
        setupStorageKind("mssql");
        ConfigValue result = interceptor.getValue(context, "quarkus.datasource.mssql.jdbc.telemetry");
        assertEquals("true", result.getValue());
    }

    // ---- Active and telemetry agree ----

    @Test
    void testActiveAndTelemetryMatchForPostgresql() {
        setupStorageKind("postgresql");

        ConfigValue active = interceptor.getValue(context, "quarkus.datasource.postgresql.active");
        ConfigValue telemetry = interceptor.getValue(context, "quarkus.datasource.postgresql.jdbc.telemetry");

        assertEquals(active.getValue(), telemetry.getValue());
        assertEquals("true", active.getValue());
    }

    @Test
    void testInactiveDatasourceHasTelemetryDisabled() {
        setupStorageKind("postgresql");

        ConfigValue h2Active = interceptor.getValue(context, "quarkus.datasource.h2.active");
        ConfigValue h2Telemetry = interceptor.getValue(context, "quarkus.datasource.h2.jdbc.telemetry");

        assertEquals("false", h2Active.getValue());
        assertEquals("false", h2Telemetry.getValue());
    }

    // ---- Existing value is respected ----

    @Test
    void testExistingActiveValueIsRespected() {
        when(context.proceed("quarkus.datasource.h2.active"))
                .thenReturn(configValue("quarkus.datasource.h2.active", "false"));
        // No need to set up storage kind — existing value takes precedence

        ConfigValue result = interceptor.getValue(context, "quarkus.datasource.h2.active");
        assertEquals("false", result.getValue());
    }

    @Test
    void testExistingTelemetryValueIsRespected() {
        when(context.proceed("quarkus.datasource.postgresql.jdbc.telemetry"))
                .thenReturn(configValue("quarkus.datasource.postgresql.jdbc.telemetry", "false"));

        ConfigValue result = interceptor.getValue(context, "quarkus.datasource.postgresql.jdbc.telemetry");
        assertEquals("false", result.getValue());
    }

    // ---- Passthrough for unrelated properties ----

    @Test
    void testUnrelatedPropertyPassesThrough() {
        ConfigValue original = configValue("some.other.property", "value");
        when(context.proceed("some.other.property")).thenReturn(original);

        ConfigValue result = interceptor.getValue(context, "some.other.property");
        assertEquals("value", result.getValue());
    }

    @Test
    void testNullStorageKindPassesThrough() {
        when(context.proceed("quarkus.datasource.h2.active")).thenReturn(null);
        when(context.proceed("apicurio.storage.sql.kind")).thenReturn(null);

        ConfigValue result = interceptor.getValue(context, "quarkus.datasource.h2.active");
        assertNull(result);
    }

    // ---- All database kinds covered for telemetry ----

    @Test
    void testAllDatabaseKindsHaveCorrectTelemetry() {
        for (RegistryDatabaseKind kind : RegistryDatabaseKind.values()) {
            setUp();
            setupStorageKind(kind.name());

            String dsName = kind.name();
            // H2 is named "h2" in both the enum and datasource config
            // Others: postgresql, mssql, mysql
            String telemetryProp = "quarkus.datasource." + dsName + ".jdbc.telemetry";
            String activeProp = "quarkus.datasource." + dsName + ".active";

            ConfigValue telemetry = interceptor.getValue(context, telemetryProp);
            ConfigValue active = interceptor.getValue(context, activeProp);

            assertEquals("true", telemetry.getValue(),
                    "Telemetry should be enabled for " + dsName + " when it is the active kind");
            assertEquals("true", active.getValue(),
                    "Active should be true for " + dsName + " when it is the selected kind");
        }
    }

    // ---- Helpers ----

    private void setupStorageKind(String kind) {
        when(context.proceed("quarkus.datasource.h2.active")).thenReturn(null);
        when(context.proceed("quarkus.datasource.postgresql.active")).thenReturn(null);
        when(context.proceed("quarkus.datasource.mysql.active")).thenReturn(null);
        when(context.proceed("quarkus.datasource.mssql.active")).thenReturn(null);
        when(context.proceed("quarkus.datasource.h2.jdbc.telemetry")).thenReturn(null);
        when(context.proceed("quarkus.datasource.postgresql.jdbc.telemetry")).thenReturn(null);
        when(context.proceed("quarkus.datasource.mysql.jdbc.telemetry")).thenReturn(null);
        when(context.proceed("quarkus.datasource.mssql.jdbc.telemetry")).thenReturn(null);
        when(context.proceed("apicurio.storage.sql.kind"))
                .thenReturn(configValue("apicurio.storage.sql.kind", kind));
    }

    private ConfigValue configValue(String name, String value) {
        return ConfigValue.builder()
                .withName(name)
                .withValue(value)
                .build();
    }
}
