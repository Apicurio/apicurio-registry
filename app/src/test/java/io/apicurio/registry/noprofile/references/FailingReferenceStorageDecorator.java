package io.apicurio.registry.noprofile.references;

import io.apicurio.registry.storage.decorator.RegistryStorageDecoratorBase;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.error.RegistryStorageException;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Test-only storage decorator that makes loading one chosen reference fail with a storage error, as a
 * transient database failure would. Active only when {@code apicurio.test.failing-reference.enabled} is set
 * (see {@link ReferenceLoadFailureTest}); otherwise the storage producer leaves it out.
 */
@ApplicationScoped
public class FailingReferenceStorageDecorator extends RegistryStorageDecoratorBase {

    static final String FAILURE_MESSAGE = "Simulated storage failure while loading a reference";

    @ConfigProperty(name = "apicurio.test.failing-reference.enabled", defaultValue = "false")
    boolean enabled;

    /** (groupId, artifactId) of the reference to fail; {@code null} fails nothing */
    private final AtomicReference<List<String>> failing = new AtomicReference<>();

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public int order() {
        return 0;
    }

    void failOn(String groupId, String artifactId) {
        failing.set(List.of(groupId, artifactId));
    }

    void reset() {
        failing.set(null);
    }

    /** Routed here by name and signature: decorators do not implement RegistryStorage. */
    public ContentWrapperDto getContentByReference(ArtifactReferenceDto reference) {
        List<String> target = failing.get();
        if (target != null && Objects.equals(target.get(0), reference.getGroupId())
                && Objects.equals(target.get(1), reference.getArtifactId())) {
            throw new RegistryStorageException(FAILURE_MESSAGE);
        }
        return delegate.getContentByReference(reference);
    }
}
