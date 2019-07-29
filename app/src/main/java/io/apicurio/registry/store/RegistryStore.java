package io.apicurio.registry.store;

import java.util.List;
import java.util.Set;

/**
 * @author Ales Justin
 */
public interface RegistryStore {
    Set<String> listSubjects();
    List<Integer> deleteSubject(String subject);

    String getSchema(Integer id);
}
