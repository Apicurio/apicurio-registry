package io.apicurio.common.apps.storage.sql.jdbi.mappers;

import java.util.Optional;

public class MapperLoaderHolder {

    public static MapperLoaderHolder INSTANCE = new MapperLoaderHolder();

    public static MapperLoaderHolder getInstance() {
        return INSTANCE;
    }

    private MapperLoader loader;

    private MapperLoaderHolder() {
    }

    public void setMapperLoader(MapperLoader loader) {
        this.loader = loader;
    }

    public Optional<MapperLoader> getMapperLoader() {
        return Optional.ofNullable(loader);
    }
}
