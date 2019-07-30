package io.apicurio.registry.store;

import io.apicurio.registry.dto.Schema;

import java.util.List;
import java.util.Set;

/**
 * @author Ales Justin
 */
public interface RegistryStore {
    Set<String> listSubjects();
    List<Integer> deleteSubject(String subject);

    String getSchema(Integer id);

    Schema findSchemaWithSubject(String subject, boolean checkDeletedSchema, String schema);

    int registerSchema(String subject, Integer id, Integer version, String schema);
}
