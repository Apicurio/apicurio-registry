package io.apicurio.registry.streams.utils;

import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.streams.processor.StateRestoreListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Ales Justin
 */
public class LoggingStateRestoreListener implements StateRestoreListener {
    private static final Logger log = LoggerFactory.getLogger(LoggingStateRestoreListener.class);

    @Override
    public void onRestoreStart(TopicPartition topicPartition, String storeName, long startingOffset, long endingOffset) {
        log.info("restore start: topicPartition={}, storeName={}, startingOffset={}, endingOffset={}", topicPartition, storeName, startingOffset, endingOffset);
    }

    @Override
    public void onBatchRestored(TopicPartition topicPartition, String storeName, long batchEndOffset, long numRestored) {
        log.info("batch restored: topicPartition={}, storeName={}, batchEndOffset={}, numRestored={}", topicPartition, storeName, batchEndOffset, numRestored);

    }

    @Override
    public void onRestoreEnd(TopicPartition topicPartition, String storeName, long totalRestored) {
        log.info("restore end: topicPartition={}, storeName={}, totalRestored={}", topicPartition, storeName, totalRestored);
    }
}
