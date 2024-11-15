package io.apicurio.common.apps.storage.exceptions;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * @author Jakub Senko <em>m@jsenko.net</em>
 */
@ApplicationScoped
@DefaultBean
@SuppressWarnings("unchecked")
public class WrappingStorageExceptionMapper implements StorageExceptionMapper {

    @Override
    public WrappedStorageException map(StorageException original) {
        return new WrappedStorageException(original);
    }
}
