package io.apicurio.registry.mcptools.compatibility;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * A producer tool's {@code outputSchema}, projected and checked once so that it can be compared
 * with any number of consumers.
 */
public final class PreparedProducer {

    private final List<CompatibilityLimitation> limitations;
    private final SchemaProjection projection;
    private final JsonNode asWritten;
    private final JsonNode closed;

    private PreparedProducer(List<CompatibilityLimitation> limitations, SchemaProjection projection,
            JsonNode asWritten, JsonNode closed) {
        this.limitations = List.copyOf(limitations);
        this.projection = projection;
        this.asWritten = asWritten;
        this.closed = closed;
    }

    static PreparedProducer unavailable(List<CompatibilityLimitation> limitations) {
        return new PreparedProducer(limitations, null, null, null);
    }

    static PreparedProducer prepared(SchemaProjection projection, JsonNode asWritten, JsonNode closed) {
        return new PreparedProducer(projection.limitations(), projection, asWritten, closed);
    }

    /**
     * Whether any consumer can be found compatible with this producer. It is {@code false} when
     * the tool has no {@code outputSchema}, or one that cannot be compared at all.
     */
    public boolean canMatch() {
        return asWritten != null;
    }

    /**
     * The limitations of the producer's {@code outputSchema}, which apply to every comparison.
     */
    public List<CompatibilityLimitation> limitations() {
        return limitations;
    }

    SchemaProjection projection() {
        return projection;
    }

    JsonNode asWritten() {
        return asWritten;
    }

    /**
     * The producer with its root object closed, or {@code null} when closing it would not change
     * what it emits.
     */
    JsonNode closed() {
        return closed;
    }
}
