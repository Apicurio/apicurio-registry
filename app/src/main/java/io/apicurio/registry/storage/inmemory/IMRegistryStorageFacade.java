package io.apicurio.registry.storage.inmemory;

import io.apicurio.registry.dto.Schema;
import io.apicurio.registry.storage.ArtifactStorage;
import io.apicurio.registry.storage.ArtifactVersionStorage;
import io.apicurio.registry.storage.CounterStorage;
import io.apicurio.registry.storage.GlobalArtifactStorage;
import io.apicurio.registry.storage.model.ArtifactId;
import io.apicurio.registry.storage.model.ArtifactVersion;
import io.apicurio.registry.storage.model.ArtifactVersionId;
import io.apicurio.registry.store.RegistryStore;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
@InMemory
public class IMRegistryStorageFacade implements RegistryStore {

    @Inject
    @InMemory
    private CounterStorage counterStorage;

    @Inject
    @InMemory
    private ArtifactStorage artifactStorage;

    @Inject
    @InMemory
    private GlobalArtifactStorage globalArtifactStorage;

    @Override
    public Set<String> listSubjects() {
        return artifactStorage.getAllKeys().stream()
                .map(ArtifactId::getArtifactId)
                .collect(Collectors.toSet());
    }

    @Override
    public List<Integer> deleteSubject(String subject) {
        ArtifactId key = new ArtifactId(subject);
        ArtifactVersionStorage artifactVersionStorage = artifactStorage.get(key);
        List<ArtifactVersionId> keys = artifactVersionStorage.getAllKeys();
        artifactStorage.delete(key);
        return keys.stream()
                .map(ArtifactVersionId::getVersionId)
                .map(Long::intValue /* TODO unsafe */)
                .collect(Collectors.toList());
    }

    @Override
    public String getSchema(Integer id) {
        return globalArtifactStorage.get(id.longValue()).getContent();
    }

    @Override
    public Schema findSchemaWithSubject(String subject, boolean checkDeletedSchema, String schema) {
        // TODO Guessing here, the interface does not contain javadoc
        ArtifactId key = new ArtifactId(subject);
        ArtifactVersionStorage artifactVersionStorage = artifactStorage.get(key);
        ArtifactVersion latestVersion = artifactVersionStorage.getLatestVersion();

        return new Schema(
                latestVersion.getId().getArtifactId(),
                latestVersion.getId().getVersionId().intValue(),
                latestVersion.getGlobalId().intValue(),
                latestVersion.getContent());
    }

    @Override
    public int registerSchema(String subject, Integer id, Integer version, String schema) {
        // TODO Does not support user selected global ID or version ID
        ArtifactVersionStorage artifactVersionStorage = artifactStorage.get(new ArtifactId(subject));
        if (artifactVersionStorage == null)
            artifactVersionStorage = artifactStorage.create(subject);
        ArtifactVersion version_ = artifactVersionStorage.create(new ArtifactVersion(null, null, schema));
        return version_.getGlobalId().intValue();
    }
}
