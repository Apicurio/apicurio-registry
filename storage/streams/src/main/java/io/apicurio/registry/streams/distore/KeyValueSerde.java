package io.apicurio.registry.streams.distore;

import org.apache.kafka.common.serialization.Serde;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class KeyValueSerde<K, V> {
    private final String serdeTopic;
    private final Serde<K> keySerde;
    private final Serde<V> valSerde;

    public KeyValueSerde(String serdeTopic, Serde<K> keySerde, Serde<V> valSerde) {
        this.serdeTopic = serdeTopic;
        this.keySerde = Objects.requireNonNull(keySerde);
        this.valSerde = Objects.requireNonNull(valSerde);
    }

    public K deserializeKey(byte[] data) {
        return keySerde.deserializer().deserialize(serdeTopic, data);
    }

    public V deserializeVal(byte[] data) {
        return valSerde.deserializer().deserialize(serdeTopic, data);
    }

    public byte[] serializeKey(K key) {
        return keySerde.serializer().serialize(serdeTopic, key);
    }

    public byte[] serializeVal(V val) {
        return valSerde.serializer().serialize(serdeTopic, val);
    }

    public static Registry newRegistry() {
        return new Registry();
    }

    // registry for multiple stores

    public static class Registry {
        private final Map<String, KeyValueSerde<?, ?>> registry = new HashMap<>();

        Registry() {
        }

        public <K, V> Registry register(String storeName, Serde<K> keySerde, Serde<V> valSerde) {
            registry.put(storeName, new KeyValueSerde<>(storeName + "-serde-topic", keySerde, valSerde));
            return this;
        }

        public <K, V> KeyValueSerde<K, V> keyValueSerde(String storeName) {
            @SuppressWarnings("unchecked")
            KeyValueSerde<K, V> kvSerde = (KeyValueSerde<K, V>) registry.get(storeName);
            if (kvSerde == null)
                throw new IllegalStateException(
                    "Key/Value Serde(s) for store with name: '" + storeName + "' are not registered.");
            return kvSerde;
        }

        public <K> K deserializeKey(String storeName, byte[] data) {
            return this.<K, Object>keyValueSerde(storeName).deserializeKey(data);
        }

        public <V> V deserializeVal(String storeName, byte[] data) {
            return this.<Object, V>keyValueSerde(storeName).deserializeVal(data);
        }

        public <K> byte[] serializeKey(String storeName, K key) {
            return this.<K, Object>keyValueSerde(storeName).serializeKey(key);
        }

        public <V> byte[] serializeVal(String storeName, V val) {
            return this.<Object, V>keyValueSerde(storeName).serializeVal(val);
        }
    }
}
