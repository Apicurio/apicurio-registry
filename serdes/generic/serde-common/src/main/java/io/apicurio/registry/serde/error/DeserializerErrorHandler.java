package io.apicurio.registry.serde.error;

import java.util.Map;

/**
 * Interface for handling records whose artifact/schema reference could not be determined or
 * resolved (e.g. a headers/payload format mismatch, a deleted schema, or an unresolvable id). By
 * default, deserializers throw when this happens; a configured handler allows a record to be
 * skipped instead, so a single permanently-unresolvable record doesn't block consumption forever.
 */
public interface DeserializerErrorHandler {

    default void configure(Map<String, Object> configs, boolean isKey) {
    }

    /**
     * Called after all normal resolution (including any configured FallbackArtifactProvider) has
     * failed for a record. Return true to have the deserializer swallow the error and skip the
     * record (deserialize() returns null); return false to preserve the default behavior of
     * throwing the original exception.
     *
     * @param topic the topic the record was read from
     * @param data the raw record payload that could not be resolved
     * @param cause the exception that resolution failed with
     * @return true to skip the record, false to rethrow {@code cause}
     */
    boolean handle(String topic, byte[] data, Exception cause);

}
