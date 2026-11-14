package io.apicurio.registry.logging.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.common.apps.config.Info;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_LOG;

/**
 * Writes each {@link AuditEvent} as one JSON object per line, at INFO level, on the
 * {@value #LOGGER_NAME} logger.
 */
@ApplicationScoped
@SuppressWarnings("java:S9436") // two loggers on purpose, see the comment on the fields
public class AuditLogSink {

    public static final String LOGGER_NAME = "io.apicurio.registry.audit.events";

    // Two loggers on purpose: AUDIT is the event stream and must carry nothing but the JSON events,
    // while LOG reports this sink's own failures. Sharing AUDIT would put non-JSON lines in the stream.
    private static final Logger AUDIT = LoggerFactory.getLogger(LOGGER_NAME);
    private static final Logger LOG = LoggerFactory.getLogger(AuditLogSink.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @ConfigProperty(name = "apicurio.audit.log.enabled", defaultValue = "true")
    @Info(category = CATEGORY_LOG, description = """
            Write audit events, one JSON object per line, to the `io.apicurio.registry.audit.events` logger at \
            INFO level. Has no effect when `apicurio.audit.enabled` is false.""", availableSince = "3.4.0")
    boolean enabled;

    void onEvent(@Observes AuditEvent event) {
        if (!enabled) {
            return;
        }
        try {
            AUDIT.info(MAPPER.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            LOG.error("Failed to serialize audit event {}", event.auditId(), e);
        }
    }
}
