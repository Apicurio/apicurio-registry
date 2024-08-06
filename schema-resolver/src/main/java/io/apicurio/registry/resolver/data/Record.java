package io.apicurio.registry.resolver.data;

/**
 * Record defines an object that is known as the data or the payload of the record and it's associated
 * metadata. A record can be message to be sent or simply an object that can be serialized and deserialized.
 */
public interface Record<T> {

    Metadata metadata();

    T payload();

}
