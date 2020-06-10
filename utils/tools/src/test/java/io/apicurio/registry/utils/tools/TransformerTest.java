/*
 * Copyright 2020 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.utils.tools;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author Ales Justin
 */
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
