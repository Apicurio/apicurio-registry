package io.apicurio.registry.storage.impl.polling.format;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.apicurio.registry.storage.impl.polling.model.Type;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Resolves the {@code $type} of a metadata file to its kind and data format version, and upgrades files
 * in older readable versions to the current one.
 * <p>
 * This is the only place that knows about format versions. The loader and the model classes work with
 * the current version only. See {@code adr/0013-gitops-data-format-versions.md}.
 */
public final class DataFormat {

    public static final String TYPE_FIELD = "$type";

    private static final Pattern TYPE_PATTERN = Pattern.compile("^([a-z]+)-v(0|[1-9][0-9]{0,3})$");

    private static final List<FormatMigration> MIGRATIONS = List.of(new V0ToV1Migration());

    private DataFormat() {
    }

    /**
     * What the registry does with a {@code $type} value.
     */
    public enum Outcome {
        /**
         * A known kind in a readable version.
         */
        READABLE,
        /**
         * A known kind in a version that is no longer read.
         */
        REMOVED,
        /**
         * A known kind in a version newer than the current one, written for a later registry release.
         */
        NEWER,
        /**
         * Not a registry metadata type.
         */
        UNKNOWN
    }

    /**
     * The result of resolving a {@code $type} value. {@code kind} is set unless the outcome is
     * {@link Outcome#UNKNOWN}; {@code version} is set for {@link Outcome#READABLE} and
     * {@link Outcome#REMOVED}.
     */
    public record Resolution(Outcome outcome, String rawType, Type kind, FormatVersion version) {

        /**
         * Returns the error to report for a file with this type, or empty if the file can be read or
         * is not a registry metadata file.
         */
        public Optional<String> error(String path) {
            return switch (outcome) {
                case REMOVED -> Optional.of(String.format(
                        "File %s uses the GitOps data format version %s ($type: %s), which was removed in %s."
                                + " Convert it to version %s ($type: %s).",
                        path, version.id(), rawType, version.removedIn(), FormatVersion.current().id(),
                        currentTypeName(kind)));
                case NEWER -> Optional.of(String.format(
                        "File %s uses $type %s, a newer GitOps data format version than this registry reads"
                                + " (the current version is %s). It requires a newer registry release.",
                        path, rawType, FormatVersion.current().id()));
                default -> Optional.empty();
            };
        }
    }

    /**
     * Resolves a {@code $type} value, for example {@code artifact-v1}.
     */
    public static Resolution resolve(String rawType) {
        if (rawType == null) {
            return new Resolution(Outcome.UNKNOWN, null, null, null);
        }
        var matcher = TYPE_PATTERN.matcher(rawType);
        if (!matcher.matches()) {
            return new Resolution(Outcome.UNKNOWN, rawType, null, null);
        }
        var kind = Type.fromKind(matcher.group(1));
        if (kind.isEmpty()) {
            return new Resolution(Outcome.UNKNOWN, rawType, null, null);
        }
        int number = Integer.parseInt(matcher.group(2));
        var version = FormatVersion.fromNumber(number);
        if (version.isEmpty()) {
            if (number > FormatVersion.current().number()) {
                return new Resolution(Outcome.NEWER, rawType, kind.get(), null);
            }
            // Every version that ever existed is listed, so this was never a valid type.
            return new Resolution(Outcome.UNKNOWN, rawType, null, null);
        }
        if (!version.get().isReadable()) {
            return new Resolution(Outcome.REMOVED, rawType, kind.get(), version.get());
        }
        return new Resolution(Outcome.READABLE, rawType, kind.get(), version.get());
    }

    /**
     * Upgrades a file in a readable version to the current version, one migration at a time, and sets
     * its {@code $type} to the current one. A file in the current version is returned unchanged.
     *
     * @param file the parsed file; it may be modified
     */
    public static ObjectNode upgrade(ObjectNode file, Type kind, FormatVersion from) {
        if (!from.isReadable()) {
            throw new IllegalArgumentException("Version " + from.id() + " is not readable");
        }
        var version = from;
        var result = file;
        while (version != FormatVersion.current()) {
            var step = migrationFrom(version);
            result = step.migrate(kind, result);
            version = step.to();
            result.put(TYPE_FIELD, typeName(kind, version));
        }
        return result;
    }

    /**
     * Returns the {@code $type} value of a kind in a version, for example {@code artifact-v1}.
     */
    public static String typeName(Type kind, FormatVersion version) {
        return kind.getKind() + "-" + version.id();
    }

    /**
     * Returns the {@code $type} value of a kind in the current version.
     */
    public static String currentTypeName(Type kind) {
        return typeName(kind, FormatVersion.current());
    }

    static List<FormatMigration> migrations() {
        return MIGRATIONS;
    }

    private static FormatMigration migrationFrom(FormatVersion version) {
        return MIGRATIONS.stream().filter(m -> m.from() == version).findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No migration from GitOps data format version " + version.id()));
    }
}
