package io.apicurio.registry.storage;

import io.apicurio.registry.utils.impexp.Entity;
import lombok.Getter;

public class ImportException extends StorageException {

    private static final long serialVersionUID = 8122199361950634285L;

    @Getter
    private final Entity entity;

    public ImportException(String reason, Entity entity) {
        super(reason);
        this.entity = entity;
    }

    public ImportException(Entity entity, Throwable cause) {
        super(cause);
        this.entity = entity;
    }

    @Override
    public String getMessage() {
        return "Import of entity '" + entity + "' has failed"
               + (super.getMessage() != null ? ": " + super.getMessage() : ".");
    }
}
