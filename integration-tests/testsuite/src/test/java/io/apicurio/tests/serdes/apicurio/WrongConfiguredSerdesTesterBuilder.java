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
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;

import io.apicurio.tests.serdes.apicurio.SerdesTester.DataGenerator;
import io.apicurio.tests.serdes.apicurio.Tester.TesterBuilder;

/**
 * @author Fabian Martinez
 */
public class WrongConfiguredSerdesTesterBuilder<P> implements TesterBuilder {

    private DataGenerator<P> dataGenerator;

    private String topic;

    private Class<?> artifactResolverStrategy;

    private Class<?> serializer;

    private Properties producerProperties = new Properties();

    public WrongConfiguredSerdesTesterBuilder() {
        super();
    }

    public WrongConfiguredSerdesTesterBuilder<P> withProducerProperty(String key, String value) {
        producerProperties.put(key, value);
        return this;
    }

    public <U extends Serializer<?>> WrongConfiguredSerdesTesterBuilder<P> withSerializer(Class<U> serializer) {
        this.serializer = serializer;
        return this;
    }

    public WrongConfiguredSerdesTesterBuilder<P> withTopic(String topic) {
        this.topic = topic;
        return this;
    }

    public WrongConfiguredSerdesTesterBuilder<P> withStrategy(Class<?> strategy) {
        this.artifactResolverStrategy = strategy;
        return this;
    }

    public WrongConfiguredSerdesTesterBuilder<P> withDataGenerator(DataGenerator<P> generator) {
        this.dataGenerator = generator;
        return this;
    }

    /**
     * @see io.apicurio.tests.serdes.apicurio.SerdesTester.TesterBuilder#build()
     */
    @Override
    public Tester build() {
        Objects.requireNonNull(producerProperties);
        Objects.requireNonNull(serializer);
        Objects.requireNonNull(topic);
        Objects.requireNonNull(artifactResolverStrategy);
        Objects.requireNonNull(dataGenerator);
        return new WrongConfiguredSerdesTester();
    }


    private class WrongConfiguredSerdesTester extends SerdesTester<String, P, Object> implements Tester {

        /**
         * @see io.apicurio.tests.serdes.apicurio.Tester#test()
         */
        @Override
        public void test() throws Exception {
            Producer<String, P> producer = this.createProducer(producerProperties, StringSerializer.class, serializer, topic, artifactResolverStrategy);

            assertThrows(ExecutionException.class, () -> this.produceMessages(producer, topic, dataGenerator, 10));

        }

    }
}
