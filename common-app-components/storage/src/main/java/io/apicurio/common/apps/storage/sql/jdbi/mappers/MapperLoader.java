package io.apicurio.common.apps.storage.sql.jdbi.mappers;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class MapperLoader {

    @Inject
    Instance<RowMapper<?>> mappers;

    void init(@Observes StartupEvent ev) {
        MapperLoaderHolder.getInstance().setMapperLoader(this);
    }

    public List<RowMapper<?>> getMappers() {
        return mappers.stream().collect(Collectors.toList());
    }
}
