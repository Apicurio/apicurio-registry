package io.apicurio.registry.storage.impl.polling;

/**
 * A structured error from polling-based data loading.
 *
 * @param detail  human-readable description of the error
 * @param source  source ID (e.g., repository ID) where the error occurred, or null for global errors
 * @param context file path or location where the error occurred, or null if not file-specific
 */
public record PollingError(String detail, String source, String context) {

    public PollingError(String detail) {
        this(detail, null, null);
    }

    @Override
    public String toString() {
        var sb = new StringBuilder();
        if (source != null) {
            sb.append("[").append(source).append("] ");
        }
        sb.append(detail);
        if (context != null) {
            sb.append(" (").append(context).append(")");
        }
        return sb.toString();
    }
}
