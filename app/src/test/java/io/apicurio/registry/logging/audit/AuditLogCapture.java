package io.apicurio.registry.logging.audit;

import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Collects the messages written to the audit logger itself (not its child loggers) while open.
 */
public class AuditLogCapture extends Handler implements AutoCloseable {

    private final List<String> lines = new CopyOnWriteArrayList<>();
    private final Logger logger = Logger.getLogger(AuditLogSink.LOGGER_NAME);

    public AuditLogCapture() {
        logger.addHandler(this);
    }

    public List<String> lines() {
        return lines;
    }

    /**
     * Writes a unique line through the audit logger and checks that it was captured, so that an empty
     * capture means nothing was logged rather than that the capture is broken.
     */
    public void assertCapturing() {
        String probe = "capture-probe-" + UUID.randomUUID();
        LoggerFactory.getLogger(AuditLogSink.LOGGER_NAME).info(probe);
        if (!lines.remove(probe)) {
            throw new AssertionError("the audit logger capture received nothing: " + lines);
        }
    }

    @Override
    public void publish(LogRecord record) {
        if (AuditLogSink.LOGGER_NAME.equals(record.getLoggerName())) {
            lines.add(record.getMessage());
        }
    }

    @Override
    public void flush() {
        // java.util.logging Handler requires this override; the capture buffers in memory, so
        // there is nothing to flush.
    }

    @Override
    public void close() {
        logger.removeHandler(this);
    }
}
