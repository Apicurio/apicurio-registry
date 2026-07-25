package io.apicurio.registry.serde.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link DeserializerErrorHandler} implementation: logs the unresolvable record at WARN
 * level and skips it.
 */
public class LoggingDeserializerErrorHandler implements DeserializerErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(LoggingDeserializerErrorHandler.class);

    @Override
    public boolean handle(String topic, byte[] data, Exception cause) {
        int dataLength = data == null ? 0 : data.length;
        log.warn("Skipping unresolvable record on topic '{}' ({} bytes): {}", topic, dataLength,
                cause.getMessage(), cause);
        return true;
    }

}
