package io.apicurio.registry.storage.impl.polling.format;

import java.util.Arrays;
import java.util.Optional;

/**
 * The versions of the GitOps data format, which GitOps and KubernetesOps storage read from metadata
 * files. The version is the suffix of the {@code $type} value, for example {@code v1} in
 * {@code artifact-v1}.
 * <p>
 * The lifecycle rules are in {@code adr/0013-gitops-data-format-versions.md}: at most two versions are
 * readable (the current one and at most one deprecated one), a deprecated version stays readable for at
 * least two minor releases, and a removed version stays listed here so that a file in it fails with a
 * clear error.
 */
public enum FormatVersion {

    V0(0, Status.DEPRECATED, "3.4.0", null),

    V1(1, Status.CURRENT, null, null);

    /**
     * The lifecycle state of a version in this release.
     */
    public enum Status {
        /**
         * Read, and written by the registry (for example by the export).
         */
        CURRENT,
        /**
         * Read, with a warning. Files are upgraded to the current version when they are loaded.
         */
        DEPRECATED,
        /**
         * Not read. A file in this version fails the load.
         */
        REMOVED
    }

    private final int number;
    private final Status status;
    private final String deprecatedIn;
    private final String removedIn;

    FormatVersion(int number, Status status, String deprecatedIn, String removedIn) {
        this.number = number;
        this.status = status;
        this.deprecatedIn = deprecatedIn;
        this.removedIn = removedIn;
    }

    /**
     * Returns the version as it appears in a {@code $type} value, for example {@code v1}.
     */
    public String id() {
        return "v" + number;
    }

    public int number() {
        return number;
    }

    public Status status() {
        return status;
    }

    public boolean isReadable() {
        return status != Status.REMOVED;
    }

    /**
     * Returns the release that deprecated this version, or {@code null} if it is not deprecated.
     */
    public String deprecatedIn() {
        return deprecatedIn;
    }

    /**
     * Returns the release that removed this version, or {@code null} if it is still readable.
     */
    public String removedIn() {
        return removedIn;
    }

    public static FormatVersion current() {
        return Arrays.stream(values()).filter(v -> v.status == Status.CURRENT).findFirst()
                .orElseThrow(() -> new IllegalStateException("No current GitOps data format version"));
    }

    public static Optional<FormatVersion> fromNumber(int number) {
        return Arrays.stream(values()).filter(v -> v.number == number).findFirst();
    }
}
