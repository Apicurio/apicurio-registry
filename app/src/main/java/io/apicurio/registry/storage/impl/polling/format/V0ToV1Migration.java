package io.apicurio.registry.storage.impl.polling.format;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.apicurio.registry.storage.impl.polling.model.Type;

/**
 * Upgrades {@code v0} files to {@code v1}. Version 1 has the same fields as version 0: it only renames
 * the format, which is no longer experimental. Only the {@code $type} changes, and {@link DataFormat}
 * sets it.
 */
final class V0ToV1Migration implements FormatMigration {

    @Override
    public FormatVersion from() {
        return FormatVersion.V0;
    }

    @Override
    public FormatVersion to() {
        return FormatVersion.V1;
    }

    @Override
    public ObjectNode migrate(Type kind, ObjectNode file) {
        return file;
    }
}
