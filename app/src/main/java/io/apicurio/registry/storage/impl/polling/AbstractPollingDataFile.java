package io.apicurio.registry.storage.impl.polling;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.impl.polling.model.Any;
import io.apicurio.registry.storage.impl.polling.model.Type;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.util.Optional;

/**
 * Abstract base class for polling data files, providing common fields and behavior
 * shared by all data file implementations (Git, Kubernetes ConfigMaps, etc.).
 */
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public abstract class AbstractPollingDataFile implements PollingDataFile {

    @EqualsAndHashCode.Include
    @Getter
    protected final String path;

    @Getter
    protected final Optional<Any> any;

    protected ContentHandle data;

    @Getter
    @Setter
    protected boolean processed;

    protected AbstractPollingDataFile(String path, ContentHandle data, Optional<Any> any) {
        this.path = path;
        this.data = data;
        this.any = any;
    }

    @Override
    public ContentHandle getData() {
        return data;
    }

    @Override
    public boolean isType(Type type) {
        return any.map(a -> type == a.getType()).orElse(false);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getEntityUnchecked() {
        return (T) any.get().getEntity();
    }
}
