package io.apicurio.registry.storage.impl.gitops;

import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.impl.gitops.model.GitFile;
import io.apicurio.registry.storage.impl.gitops.model.Type;
import io.apicurio.registry.storage.impl.gitops.model.v0.Registry;
import lombok.Getter;
import lombok.Setter;
import org.eclipse.jgit.revwalk.RevCommit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.unmodifiableList;
import static java.util.Collections.unmodifiableSet;


public class ProcessingState {

    @Getter
    private RegistryStorage storage;

    @Getter
    @Setter
    private Registry currentRegistry;

    @Getter
    @Setter
    private RevCommit updatedCommit;

    private final List<String> errors = new ArrayList<>();

    @Getter
    private final Map<String, GitFile> pathIndex = new HashMap<>();

    private final Map<Type, Set<GitFile>> typeIndex = new HashMap<>();


    public ProcessingState(RegistryStorage storage) {
        this.storage = storage;
    }

    public void recordError(String message, Object... params) {
        errors.add(String.format(message, params));
    }

    public boolean isSuccessful() {
        return errors.isEmpty();
    }

    public List<String> getErrors() {
        return unmodifiableList(errors);
    }

    public boolean isCurrentRegistryId(String id) {
        return currentRegistry.getId().equals(id);
    }

    public Set<GitFile> fromTypeIndex(Type type) {
        return unmodifiableSet(typeIndex.computeIfAbsent(type, k -> new HashSet<>()));
    }

    public void index(GitFile file) {
        pathIndex.put(file.getPath(), file);
        file.getAny().ifPresent(a -> typeIndex.computeIfAbsent(a.getType(), k -> new HashSet<>()).add(file));
    }
}
