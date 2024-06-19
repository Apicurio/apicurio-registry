package io.apicurio.registry.utils.converter.json;

import io.apicurio.registry.resolver.strategy.ArtifactReference;
import io.apicurio.registry.serde.AbstractKafkaSerDe;
import io.apicurio.registry.serde.DefaultIdHandler;
import io.apicurio.registry.serde.IdHandler;

import java.nio.ByteBuffer;
import java.util.Objects;

public class CompactFormatStrategy implements FormatStrategy {

    private IdHandler idHandler;

    public CompactFormatStrategy() {
        this(new DefaultIdHandler());
    }

    public CompactFormatStrategy(IdHandler idHandler) {
        setIdHandler(idHandler);
    }

    public void setIdHandler(IdHandler idHandler) {
        this.idHandler = Objects.requireNonNull(idHandler);
    }

    @Override
    public byte[] fromConnectData(long globalId, byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.allocate(1 + idHandler.idSize() + bytes.length);
        buffer.put(AbstractKafkaSerDe.MAGIC_BYTE);
        idHandler.writeId(ArtifactReference.fromGlobalId(globalId), buffer);
        buffer.put(bytes);
        return buffer.array();
    }

    @Override
    public IdPayload toConnectData(byte[] bytes) {
        ByteBuffer buffer = AbstractKafkaSerDe.getByteBuffer(bytes);
        ArtifactReference reference = idHandler.readId(buffer);
        long globalId = reference.getGlobalId();
        byte[] payload = new byte[bytes.length - idHandler.idSize() - 1];
        buffer.get(payload);
        return new IdPayload(globalId, payload);
    }
}
