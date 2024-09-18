package io.apicurio.registry.utils.converter.json;

import io.apicurio.registry.resolver.strategy.ArtifactReference;
import io.apicurio.registry.serde.BaseSerde;
import io.apicurio.registry.serde.Default4ByteIdHandler;
import io.apicurio.registry.serde.IdHandler;

import java.nio.ByteBuffer;
import java.util.Objects;

public class CompactFormatStrategy implements FormatStrategy {

    private IdHandler idHandler;

    public CompactFormatStrategy() {
        this(new Default4ByteIdHandler());
    }

    public CompactFormatStrategy(IdHandler idHandler) {
        setIdHandler(idHandler);
    }

    public void setIdHandler(IdHandler idHandler) {
        this.idHandler = Objects.requireNonNull(idHandler);
    }

    @Override
    public byte[] fromConnectData(long contentId, byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.allocate(1 + idHandler.idSize() + bytes.length);
        buffer.put(BaseSerde.MAGIC_BYTE);
        idHandler.writeId(ArtifactReference.fromContentId(contentId), buffer);
        buffer.put(bytes);
        return buffer.array();
    }

    @Override
    public IdPayload toConnectData(byte[] bytes) {
        ByteBuffer buffer = BaseSerde.getByteBuffer(bytes);
        ArtifactReference reference = idHandler.readId(buffer);
        long contentId = reference.getContentId();
        byte[] payload = new byte[bytes.length - idHandler.idSize() - 1];
        buffer.get(payload);
        return new IdPayload(contentId, payload);
    }
}
