package io.apicurio.registry.store.im;

import io.apicurio.registry.store.RegistryStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.enterprise.context.ApplicationScoped;

/**
 * Testing / development only!
 *
 * @author Ales Justin
 */
@ApplicationScoped
public class InMemoryRegistryStore implements RegistryStore {
    private final Map<String, String> schemas = new ConcurrentHashMap<>();

    @Override
    public Set<String> listSubjects() {
        return schemas.keySet();
    }

    @Override
    public List<Integer> deleteSubject(String subject) {
        List<Integer> versions = new ArrayList<>();
        return versions;
    }

    @Override
    public String getSchema(Integer id) {
        return "{s:1}";
    }
}
