package io.apicurio.registry.storage.impl.kafkasql;

public abstract class AbstractMessage implements KafkaSqlMessage {

    /**
     * @see io.apicurio.registry.storage.impl.kafkasql.KafkaSqlMessage#getKey()
     */
    @Override
    public KafkaSqlMessageKey getKey() {
        return KafkaSqlMessageKey.builder().messageType(getClass().getSimpleName()).build();
    }

}
