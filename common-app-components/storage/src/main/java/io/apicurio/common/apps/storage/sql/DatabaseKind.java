package io.apicurio.common.apps.storage.sql;

public enum DatabaseKind {

    postgresql("org.postgresql.Driver"), h2("org.h2.Driver"), mssql(
            "com.microsoft.sqlserver.jdbc.SQLServerDriver");

    final String driverClassName;

    DatabaseKind(String driverClassName) {
        this.driverClassName = driverClassName;
    }

    public String getDriverClassName() {
        return driverClassName;
    }
}
