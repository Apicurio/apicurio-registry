package io.apicurio.registry.mcptools.compatibility;

import org.everit.json.schema.Schema;

import java.util.List;

/**
 * A producer tool's {@code outputSchema}, projected and loaded once so that it can be compared
 * with any number of consumers.
 */
public final class PreparedProducer {

    private final List<CompatibilityLimitation> limitations;
    private final SchemaProjection projection;
    private final Schema asWritten;
    private final Schema closed;

    private PreparedProducer(List<CompatibilityLimitation> limitations, SchemaProjection projection,
            Schema asWritten, Schema closed) {
        this.limitations = List.copyOf(limitations);
        this.projection = projection;
        this.asWritten = asWritten;
        this.closed = closed;
    }

    static PreparedProducer unavailable(List<CompatibilityLimitation> limitations) {
        return new PreparedProducer(limitations, null, null, null);
    }

    static PreparedProducer loaded(SchemaProjection projection, Schema asWritten, Schema closed) {
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

    Schema asWritten() {
        return asWritten;
    }

    /**
     * The producer with its root object closed, or {@code null} when closing it would not change
     * what it emits.
     */
    Schema closed() {
        return closed;
    }
}
