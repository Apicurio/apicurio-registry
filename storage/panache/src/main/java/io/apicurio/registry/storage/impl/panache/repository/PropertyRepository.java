package io.apicurio.registry.storage.impl.panache.repository;

import io.apicurio.registry.storage.impl.panache.entity.Property;
import io.apicurio.registry.storage.impl.panache.entity.Version;
import io.quarkus.hibernate.orm.panache.PanacheRepository;

import javax.enterprise.context.ApplicationScoped;
import java.util.Map;

@ApplicationScoped
public class PropertyRepository implements PanacheRepository<Property> {

    public void persistProperties(Map<String, String> properties, Version version) {

        if (properties != null && !properties.isEmpty()) {
            properties.forEach((k, v) -> {

                Property property = new Property();
                property.pkey = k;
                property.pvalue = v;
                property.version = version;
                persist(property);
            });
        }
    }
}
