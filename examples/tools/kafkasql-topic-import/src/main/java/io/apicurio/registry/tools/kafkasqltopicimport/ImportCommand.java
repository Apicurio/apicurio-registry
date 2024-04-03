/*
 * Copyright 2023 JBoss Inc
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

package io.apicurio.registry.tools.kafkasqltopicimport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Streams;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.slf4j.simple.SimpleLogger;
import picocli.CommandLine.Command;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static picocli.CommandLine.Option;

/**
 * @author Jakub Senko <em>m@jsenko.net</em>
 */
@Command(name = "import", version = "0.1", mixinStandardHelpOptions = true)
public class ImportCommand implements Runnable {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Option(names = {"-b", "--bootstrap-sever"}, description = "Kafka bootstrap server URL.",
            required = true, defaultValue = "localhost:9092")
    private String kafkaBootstrapServer;

    @Option(names = {"-f", "--file"}, description = "Path to a kafkasql-journal topic dump file. " +
            "Messages must use a JSON envelope and have base64-encoded keys and values.", required = true)
    private String dumpFilePath;

    @Option(names = {"-d", "--debug"}, description = "Print debug log messages.", defaultValue = "false")
    private boolean debug;

    public void run() {

        if(debug) {
            System.setProperty(org.slf4j.simple.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "DEBUG");
        } else {
            System.setProperty(org.slf4j.simple.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "WARN");
        }

        try (Producer<byte[], byte[]> producer = createKafkaProducer()) {

            try (BufferedReader br = new BufferedReader(new FileReader(dumpFilePath))) {
                String line;
                while ((line = br.readLine()) != null) {
                    var envelope = mapper.readValue(line, Envelope.class);

                    if (envelope.getHeaders() == null) {
                        envelope.setHeaders(List.of());
                    }
                    if (envelope.getHeaders().size() % 2 != 0) {
                        throw new RuntimeException("Invalid length of the headers field: " + envelope.getHeaders().size());
                    }

                    var key = envelope.getKey() != null ? Base64.getDecoder().decode(envelope.getKey()) : null;
                    var value = envelope.getPayload() != null ? Base64.getDecoder().decode(envelope.getPayload()) : null;

                    var record = new ProducerRecord<>(
                            envelope.getTopic(),
                            envelope.getPartition(),
                            envelope.getTs(),
                            key,
                            value,
                            Streams.zip(
                                            Streams.zip(
                                                    IntStream.range(0, Integer.MAX_VALUE).boxed(),
                                                    envelope.getHeaders().stream(),
                                                    Tuple::new
                                            ).filter(t -> t.getA() % 2 == 0).map(Tuple::getB), // Even indexes: 0,2,4,...
                                            Streams.zip(
                                                    IntStream.range(0, Integer.MAX_VALUE).boxed(),
                                                    envelope.getHeaders().stream(),
                                                    Tuple::new
                                            ).filter(t -> t.getA() % 2 == 1).map(Tuple::getB), // Odd indexes: 1,3,5,...
                                            (k, v) -> new RecordHeader(k, v.getBytes(StandardCharsets.UTF_8)))
                                    .collect(Collectors.toList())
                    );
                    producer.send(record);
                }
            }

            producer.flush();
            System.err.println("Data imported successfully.");

        } catch (Exception ex) {
            System.err.println("Data import failed: " + ex.getMessage());
            ex.printStackTrace(System.err);
        }
    }


    private Producer<byte[], byte[]> createKafkaProducer() {

        Properties props = new Properties();

        props.putIfAbsent(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServer);
        props.putIfAbsent(ProducerConfig.CLIENT_ID_CONFIG, "Producer-kafkasql-journal");
        props.putIfAbsent(ProducerConfig.ACKS_CONFIG, "all");
        props.putIfAbsent(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        props.putIfAbsent(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());

        return new KafkaProducer<>(props);
    }
}
