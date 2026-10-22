package io.apicurio.registry.resolver.data;

/**
 * Record defines an object that is known as the data or the payload of the record and it's associated
 * metadata. A record can be message to be sent or simply an object that can be serialized and deserialized.
 */
public interface Record<T> {

    /**
     * Returns the metadata associated with this record, or {@code null} if no metadata is available.
     * Callers must handle a {@code null} return value.
     *
     * @return the record metadata, or {@code null}
     */
    Metadata metadata();

    T payload();

}
