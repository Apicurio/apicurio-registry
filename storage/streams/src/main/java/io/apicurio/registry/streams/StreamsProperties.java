package io.apicurio.registry.streams;

import java.util.Properties;

/**
 * @author Ales Justin
 */
public interface StreamsProperties {
    Properties getProperties();
    long toGlobalId(long offset, int partition);
    long getBaseOffset();
    String getStorageStoreName();
    String getGlobalIdStoreName();
    String getStorageTopic();
    String getGlobalIdTopic();
    String getApplicationServer();
}
