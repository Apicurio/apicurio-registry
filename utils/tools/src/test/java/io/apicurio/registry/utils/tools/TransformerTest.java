package io.apicurio.registry.utils.tools;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.concurrent.ThreadLocalRandom;

public class TransformerTest {

    @Test
    public void testType() {
        ByteBuffer buffer = ByteBuffer.allocate(30);
        buffer.put((byte) 0x0);
        buffer.putInt(42);
        byte[] bytes = new byte[25];
        ThreadLocalRandom.current().nextBytes(bytes);
        buffer.put(bytes);

        byte[] input = buffer.array();
        byte[] output = Transformer.Type.CONFLUENT_TO_APICURIO.apply(input);
        byte[] copy = Transformer.Type.APICURIO_TO_CONFLUENT.apply(output);
        Assertions.assertArrayEquals(input, copy);

        buffer = ByteBuffer.allocate(30);
        buffer.put((byte) 0x0);
        buffer.putLong(42L);
        bytes = new byte[21];
        ThreadLocalRandom.current().nextBytes(bytes);
        buffer.put(bytes);

        input = buffer.array();
        output = Transformer.Type.APICURIO_TO_CONFLUENT.apply(input);
        copy = Transformer.Type.CONFLUENT_TO_APICURIO.apply(output);
        Assertions.assertArrayEquals(input, copy);
    }
}
