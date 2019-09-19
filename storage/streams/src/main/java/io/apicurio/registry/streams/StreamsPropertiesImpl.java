package io.apicurio.registry.streams;

import org.apache.kafka.streams.StreamsConfig;

import java.util.Properties;

/**
 * @author Ales Justin
 */
public class StreamsPropertiesImpl implements StreamsProperties {
    private final Properties properties;

    public StreamsPropertiesImpl(Properties properties) {
        this.properties = properties;
    }

    public Properties getProperties() {
        return properties;
    }

    public long toGlobalId(long offset, int partition) {
        return getBaseOffset() + (offset << 16) + partition;
    }

    // just to make sure we can always move the whole system
    // and not get duplicates; e.g. after move baseOffset = max(globalId) + 1
    public long getBaseOffset() {
        return Long.parseLong(properties.getProperty("storage.base.offset", "0"));
    }

    public String getStorageStoreName() {
        return properties.getProperty("storage.store", "storage-store");
    }

    public String getGlobalIdStoreName() {
        return properties.getProperty("global.id.store", "global-id-store");
    }

    public String getStorageTopic() {
        return properties.getProperty("storage.topic", "storage-topic");
    }

    public String getGlobalIdTopic() {
        return properties.getProperty("global.id.topic", "global-id-topic");
    }

    public String getApplicationServer() {
        return properties.getProperty(StreamsConfig.APPLICATION_SERVER_CONFIG, "localhost:9000");
    }
}
