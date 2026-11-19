package io.apicurio.registry.examples.camelkafka;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Keeps what the two consumer routes received, so that it can be read over HTTP: the greetings
 * deserialized by the Avro deserializer, and the first bytes of the raw records.
 */
@ApplicationScoped
@Named("receivedGreetings")
public class ReceivedGreetings {

    private final List<Map<String, Object>> greetings = new CopyOnWriteArrayList<>();
    private final List<Map<String, Object>> raw = new CopyOnWriteArrayList<>();

    public void addGreeting(Greeting greeting) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("name", greeting.getName());
        entry.put("message", greeting.getMessage());
        entry.put("timestamp", greeting.getTimestamp());
        greetings.add(entry);
    }

    /**
     * Records the wire format of a record: a magic byte, then the 4-byte schema ID that the
     * serializer wrote (by default, the content ID).
     */
    public void addRaw(String key, byte[] value) {
        ByteBuffer buffer = ByteBuffer.wrap(value);
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("key", key);
        entry.put("magicByte", buffer.get());
        entry.put("schemaId", buffer.getInt());
        entry.put("length", value.length);
        raw.add(entry);
    }

    public List<Map<String, Object>> greetings() {
        return new ArrayList<>(greetings);
    }

    public List<Map<String, Object>> raw() {
        return new ArrayList<>(raw);
    }
}
