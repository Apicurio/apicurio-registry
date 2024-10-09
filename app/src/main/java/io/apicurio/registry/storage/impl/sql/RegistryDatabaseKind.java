package io.apicurio.registry.storage.impl.sql;

public enum RegistryDatabaseKind {

    postgresql("org.postgresql.Driver"), h2("org.h2.Driver"), mssql(
            "com.microsoft.sqlserver.jdbc.SQLServerDriver"), mysql("org.mariadb.jdbc.Driver");

    final String driverClassName;

    RegistryDatabaseKind(String driverClassName) {
        this.driverClassName = driverClassName;
    }

    public String getDriverClassName() {
        return driverClassName;
    }
}
