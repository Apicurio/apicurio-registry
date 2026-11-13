package io.apicurio.registry.storage.impl.polling.format;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.apicurio.registry.storage.impl.polling.model.Type;

/**
 * One step that upgrades a metadata file from a data format version to the next one.
 * <p>
 * A migration works on the parsed tree of a single file, before the file is bound to the model classes,
 * so the loader and the model only know the current version. {@link DataFormat} applies the steps in
 * order and sets the {@code $type} of the result, so a migration only changes the fields.
 */
public interface FormatMigration {

    /**
     * The version this step reads.
     */
    FormatVersion from();

    /**
     * The version this step produces. It must be the version after {@link #from()}.
     */
    FormatVersion to();

    /**
     * Upgrades one file of the given kind. The tree may be modified in place.
     *
     * @return the upgraded tree
     */
    ObjectNode migrate(Type kind, ObjectNode file);
}
