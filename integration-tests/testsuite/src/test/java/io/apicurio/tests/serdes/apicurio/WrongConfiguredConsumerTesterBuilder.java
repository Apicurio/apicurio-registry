/*
 * Copyright 2021 Red Hat
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

package io.apicurio.tests.serdes.apicurio;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Objects;
import java.util.concurrent.ExecutionException;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

/**
 * @author Fabian Martinez
 */
public class WrongConfiguredConsumerTesterBuilder<P, C> extends SimpleSerdesTesterBuilder<P, C> {

    /**
     * @see io.apicurio.tests.serdes.apicurio.SerdesTester.TesterBuilder#build()
     */
    @Override
    public Tester build() {
        Objects.requireNonNull(producerProperties);
        Objects.requireNonNull(consumerProperties);
        Objects.requireNonNull(serializer);
        Objects.requireNonNull(topic);
        Objects.requireNonNull(artifactResolverStrategy);
        Objects.requireNonNull(dataGenerator);
        Objects.requireNonNull(deserializer);
        Objects.requireNonNull(dataValidator);
        return new FailingConsumerTester();
    }


    private class FailingConsumerTester extends SerdesTester<String, P, C> implements Tester {

        /**
         * @see io.apicurio.tests.serdes.apicurio.Tester#test()
         */
        @Override
        public void test() throws Exception {
            Producer<String, P> producer = this.createProducer(producerProperties, StringSerializer.class, serializer, topic, artifactResolverStrategy);

            int messageCount = 10;
            this.produceMessages(producer, topic, dataGenerator, messageCount);

            if (afterProduceValidator != null) {
                assertTrue(afterProduceValidator.validate(), "After produce validation failed");
            }

            Consumer<String, C> consumer = this.createConsumer(consumerProperties, StringDeserializer.class, deserializer, topic);

            assertThrows(ExecutionException.class, () -> this.consumeMessages(consumer, topic, messageCount, dataValidator));
        }

    }

}
