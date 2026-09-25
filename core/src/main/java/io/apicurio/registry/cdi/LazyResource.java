package io.apicurio.registry.cdi;

import io.apicurio.registry.exception.RuntimeAssertionFailedException;
import io.apicurio.registry.types.RegistryException;
import jakarta.annotation.PreDestroy;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * An abstract class designed for extension by an application-scoped bean.
 * Can't be used directly because of CDI limitations.
 * <p>
 * Represents a resource that can be repeatedly created and then closed as needed to save resources.
 * <p>
 * Can be created either by using the constructor,
 * or by overriding the {@link #create()} or optionally the {@link #closeInternal(T)} methods.
 */
public abstract class LazyResource<T> {

    private T resource;

    private Supplier<T> create;
    private Consumer<T> close;

    /**
     * Must override create() if using this constructor.
     */
    protected LazyResource() {
    }

    /**
     * @param create is required
     * @param close  is optional
     */
    protected LazyResource(Supplier<T> create, Consumer<T> close) {
        this.create = create;
        this.close = close;
    }

    protected T create() {
        if (create == null) {
            throw new RuntimeAssertionFailedException("No create function provided.");
        }
        return create.get();
    }

    public final synchronized T get() {
        if (resource == null) {
            resource = create();
        }
        return resource;
    }

    protected void closeInternal(T resource) {
        if (close != null) {
            close.accept(resource);
        } else if (resource instanceof AutoCloseable ac) {
            try {
                ac.close();
            } catch (Exception e) {
                throw new RegistryException("Failed to close resource: " + resource.getClass().getCanonicalName(), e);
            }
        }
    }

    @PreDestroy
    public final synchronized void close() {
        if (resource != null) {
            closeInternal(resource);
            resource = null;
        }
    }
}
