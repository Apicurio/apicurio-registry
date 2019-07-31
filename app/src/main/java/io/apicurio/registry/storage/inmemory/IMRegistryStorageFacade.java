package io.apicurio.registry.storage.inmemory;

import io.apicurio.registry.dto.Schema;
import io.apicurio.registry.storage.ArtifactSequence;
import io.apicurio.registry.storage.model.Artifact;
import io.apicurio.registry.storage.model.ArtifactId;
import io.apicurio.registry.storage.model.ArtifactSequenceId;
import io.apicurio.registry.store.RegistryStore;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// TODO finish
@ApplicationScoped
@InMemory
public class IMRegistryStorageFacade implements RegistryStore {

    @Inject
    @InMemory
    private IMCounterStorage counterStorage;

    @Inject
    @InMemory
    private IMArtifactSequenceStorage sequenceStorage;

    @Inject
    @InMemory
    private IMArtifactStorage artifactStorage;

    @Override
    public Set<String> listSubjects() {
        return sequenceStorage.getAllKeys().stream()
                .map(ArtifactSequenceId::getSequence)
                .collect(Collectors.toSet());
    }

    @Override
    public List<Integer> deleteSubject(String subject) {
        ArtifactSequenceId key = new ArtifactSequenceId(subject);
        ArtifactSequence artifactSequence = sequenceStorage.getByKey(key);
        List<ArtifactId> keys = artifactSequence.getAllKeys();
        sequenceStorage.delete(key);
        return keys.stream()
                .map(ArtifactId::getVersion)
                .map(Long::intValue /* TODO unsafe */)
                .collect(Collectors.toList());
    }

    @Override
    public String getSchema(Integer id) {
        return artifactStorage.getByKey(id.longValue()).getContent();
    }

    @Override
    public Schema findSchemaWithSubject(String subject, boolean checkDeletedSchema, String schema) {
        /*
         * TODO The interface does not contain javadoc
         */
        ArtifactSequenceId key = new ArtifactSequenceId(subject);
        ArtifactSequence sequence = sequenceStorage.getByKey(key);
        ArtifactId latestVersion = sequence.getLatestVersion();
        Artifact artifact = sequence.getByKey(latestVersion);

        return new Schema(latestVersion.getSequence(), latestVersion.getVersion().intValue(),
                latestVersion.getId().intValue(), artifact.getContent());
    }

    @Override
    public int registerSchema(String subject, Integer id, Integer version, String schema) {
        // TODO
        return 0;
    }
}
