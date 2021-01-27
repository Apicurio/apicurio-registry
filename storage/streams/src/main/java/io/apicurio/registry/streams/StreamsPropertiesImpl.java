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
        return Long.parseLong(properties.getProperty("artifactStore.base.offset", "0"));
    }

    public String getStorageStoreName() {
        return properties.getProperty("artifactStore.store", "artifactStore-store");
    }

    public String getGlobalIdStoreName() {
        return properties.getProperty("globalIdStore.id.store", "globalIdStore-id-store");
    }

    public String getStorageTopic() {
        return properties.getProperty("artifactStore.topic", "artifactStore-topic");
    }

    public String getGlobalIdTopic() {
        return properties.getProperty("globalIdStore.id.topic", "globalIdStore-id-topic");
    }

    public String getApplicationServer() {
        return properties.getProperty(StreamsConfig.APPLICATION_SERVER_CONFIG, "localhost:9000");
    }

    public boolean ignoreAutoCreate() {
        return Boolean.parseBoolean(properties.getProperty("ignore.auto-create", "false"));
    }
}
