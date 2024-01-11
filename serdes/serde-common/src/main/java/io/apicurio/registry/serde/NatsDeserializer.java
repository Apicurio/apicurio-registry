package io.apicurio.registry.serde;

public interface NatsDeserializer<DATA> extends Configurable {


    DATA deserialize(String subject, byte[] data);
}
