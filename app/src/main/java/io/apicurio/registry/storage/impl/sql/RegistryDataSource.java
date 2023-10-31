package io.apicurio.registry.storage.impl.sql;

import io.agroal.api.AgroalDataSource;

public class RegistryDataSource {

    AgroalDataSource dataSourceInstance;

    public RegistryDataSource(AgroalDataSource dataSource) {
        this.dataSourceInstance = dataSource;
    }

    public AgroalDataSource getDataSourceInstance() {
        return dataSourceInstance;
    }
}
