package io.apicurio.registry.serde;

public interface NatsSerializer<DATA> extends Configurable {


    byte[] serialize(String subject, DATA data);
}
