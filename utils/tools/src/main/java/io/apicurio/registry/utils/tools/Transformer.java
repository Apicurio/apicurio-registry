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

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.ValueTransformer;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Properties;
import java.util.function.Function;

/**
 * Transform messages between Confluent and Apicurio format.
 *
 * To start from input topic's beginning, use this config
 * * auto.offset.reset=earliest / ConsumerConfig.AUTO_OFFSET_RESET_CONFIG
 *
 * @author Ales Justin
 */
public class Transformer {
    private static final Logger log = LoggerFactory.getLogger(Transformer.class);

    enum Type implements Function<byte[], byte[]> {
        CONFLUENT_TO_APICURIO(bytes -> {
            ByteBuffer input = ByteBuffer.wrap(bytes);
            ByteBuffer output = ByteBuffer.allocate(bytes.length + 4); // 4more due to long
            output.put(input.get()); // magic
            output.putLong(input.getInt());
            output.put(input);
            return output.array();
        }),
        APICURIO_TO_CONFLUENT(bytes -> {
            ByteBuffer input = ByteBuffer.wrap(bytes);
            ByteBuffer output = ByteBuffer.allocate(bytes.length - 4); // 4more less to int
            output.put(input.get()); // magic
            output.putInt((int) input.getLong());
            output.put(input);
            return output.array();
        });

        private Function<byte[], byte[]> fn;

        Type(Function<byte[], byte[]> fn) {
            this.fn = fn;
        }

        @Override
        public byte[] apply(byte[] bytes) {
            return fn.apply(bytes);
        }
    }

    public static void main(String[] args) {
        Properties properties = new Properties();
        for (String arg : args) {
            String[] split = arg.split("=");
            properties.put(split[0], split[1]);
        }

        String appId = properties.getProperty(StreamsConfig.APPLICATION_ID_CONFIG);
        if (appId == null) {
            properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "apicurio-registry-transformer");
        }

        String inputTopic = properties.getProperty("input-topic");
        if (inputTopic == null) {
            throw new IllegalArgumentException("Missing input topic!");
        }

        String outputTopic = properties.getProperty("output-topic");
        if (outputTopic == null) {
            throw new IllegalArgumentException("Missing output topic!");
        }

        String fnType = properties.getProperty("type");
        if (fnType == null) {
            throw new IllegalArgumentException("Missing transformation type!");
        }
        Type type = Type.valueOf(fnType);

        log.info(String.format("Transforming: %s --> %s [%s]", inputTopic, outputTopic, type));

        StreamsBuilder builder = new StreamsBuilder();
        KStream<String, byte[]> input = builder.stream(
            inputTopic,
            Consumed.with(Serdes.String(), Serdes.ByteArray())
        );

        input.transformValues(() -> new ValueTransformer<byte[], byte[]>() {
            @Override
            public void init(ProcessorContext context) {
            }

            @Override
            public byte[] transform(byte[] value) {
                return type.apply(value);
            }

            @Override
            public void close() {
            }
        }).to(outputTopic, Produced.with(Serdes.String(), Serdes.ByteArray()));

        Topology topology = builder.build(properties);
        KafkaStreams streams = new KafkaStreams(topology, properties);

        Runtime.getRuntime().addShutdownHook(new Thread(streams::close));

        streams.start();
    }
}
