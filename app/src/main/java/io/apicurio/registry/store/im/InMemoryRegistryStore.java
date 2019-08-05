package io.apicurio.registry.store.im;

import io.apicurio.registry.dto.Schema;
import io.apicurio.registry.dto.SchemaEntity;
import io.apicurio.registry.storage.inmemory.InMemory;
import io.apicurio.registry.store.IdGenerator;
import io.apicurio.registry.store.RegistryStore;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

/**
 * Testing / development only!
 *
 * @author Ales Justin
 */
@ApplicationScoped
public class InMemoryRegistryStore implements RegistryStore {
    private final Map<String, Set<SchemaEntity>> schemas = new ConcurrentHashMap<>();
    private final Map<Integer, Schema> schemasIds = new ConcurrentHashMap<>();

    @Inject
    @InMemory
    IdGenerator idGenerator;

    @Override
    public Set<String> listSubjects() {
        return schemas.keySet();
    }

    @Override
    public List<Integer> deleteSubject(String subject) {
        Set<SchemaEntity> set = schemas.get(subject);
        return (set == null ?
                Collections.emptyList() :
                set.stream().map(s -> {
                    s.setDeleted(true);
                    return s.getVersion();
                }).collect(Collectors.toList()));
    }

    @Override
    public String getSchema(Integer id) {
        Schema schema = schemasIds.get(id);
        return (schema != null ? schema.getSchema() : null);
    }

    @Override
    public Schema findSchemaWithSubject(String subject, boolean checkDeletedSchema, String schema) {
        Set<SchemaEntity> set = schemas.get(subject);
        return (set == null ?
                null :
                set.stream()
                   .min(Comparator.reverseOrder())
                   .filter(s -> checkDeletedSchema || !s.isDeleted())
                   .orElse(null));
    }

    @Override
    public int registerSchema(String subject, Integer id, Integer version, String schema) {
        if (id != null && id > 0) {
            // TODO - check for duplicate id !
        } else {
            id = idGenerator.nextId();
            SchemaEntity se = new SchemaEntity(subject, version, id, schema);
            schemasIds.put(id, se);

            schemas.compute(subject, (s, entities) -> {
                if (entities == null || entities.isEmpty()) {
                    entities = new TreeSet<>();
                }
                se.setVersion(entities.size() + 1);
                entities.add(se);
                return entities;
            });
        }
        return id;
    }
}
