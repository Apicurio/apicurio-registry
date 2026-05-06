package io.apicurio.registry.cli.services;

import java.util.Comparator;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Lenient version parser that handles both standard semver (3.2.4) and
 * Red Hat suffixed versions (3.1.6.redhat-00011, 2.6.6.Final-redhat-00001).
 * <p>
 * Versions that cannot be parsed are preserved as-is ({@link #isParsed()} returns false).
 * They cannot be compared or sorted but are still included in update candidate lists.
 */
public final class CliVersion implements Comparable<CliVersion> {

    private static final Pattern REDHAT_SUFFIX = Pattern.compile(
            "^(\\d+)\\.(\\d+)\\.(\\d+)(?:\\.(.*?))?[.-]redhat-(\\d+)$"
    );
    private static final Pattern COMMUNITY = Pattern.compile(
            "^(\\d+)\\.(\\d+)\\.(\\d+)(?:-(.+))?$"
    );

    public static final Comparator<CliVersion> COMPARATOR = Comparator
            .comparingInt(CliVersion::major)
            .thenComparingInt(CliVersion::minor)
            .thenComparingInt(CliVersion::patch)
            .thenComparingInt(CliVersion::redhatBuild);

    private final int major;
    private final int minor;
    private final int patch;
    private final int redhatBuild;
    private final boolean parsed;
    private final String original;

    private CliVersion(int major, int minor, int patch, int redhatBuild, boolean parsed, String original) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.redhatBuild = redhatBuild;
        this.parsed = parsed;
        this.original = original;
    }

    public static CliVersion parse(String version) {
        if (version == null || version.isBlank()) {
            return null;
        }
        version = version.trim();

        var m = REDHAT_SUFFIX.matcher(version);
        if (m.matches()) {
            return new CliVersion(
                    Integer.parseInt(m.group(1)),
                    Integer.parseInt(m.group(2)),
                    Integer.parseInt(m.group(3)),
                    Integer.parseInt(m.group(5)),
                    true, version
            );
        }

        m = COMMUNITY.matcher(version);
        if (m.matches()) {
            return new CliVersion(
                    Integer.parseInt(m.group(1)),
                    Integer.parseInt(m.group(2)),
                    Integer.parseInt(m.group(3)),
                    0, true, version
            );
        }

        // Fallback: extract MAJOR.MINOR.PATCH from dotted string
        var parts = version.split("\\.");
        if (parts.length >= 3) {
            try {
                return new CliVersion(
                        Integer.parseInt(parts[0]),
                        Integer.parseInt(parts[1]),
                        Integer.parseInt(parts[2]),
                        0, true, version
                );
            } catch (NumberFormatException ignored) {
            }
        }

        return new CliVersion(0, 0, 0, 0, false, version);
    }

    public int major() {
        return major;
    }

    public int minor() {
        return minor;
    }

    public int patch() {
        return patch;
    }

    public int redhatBuild() {
        return redhatBuild;
    }

    public boolean isParsed() {
        return parsed;
    }

    public boolean isProductized() {
        return redhatBuild > 0;
    }

    public boolean sameMajorMinor(CliVersion other) {
        return parsed && other.parsed && major == other.major && minor == other.minor;
    }

    public boolean isNewerThan(CliVersion other) {
        if (!parsed || !other.parsed) {
            return false;
        }
        return compareTo(other) > 0;
    }

    @Override
    public int compareTo(CliVersion other) {
        return COMPARATOR.compare(this, other);
    }

    @Override
    public String toString() {
        return original;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CliVersion that)) return false;
        if (!parsed || !that.parsed) return Objects.equals(original, that.original);
        return major == that.major && minor == that.minor && patch == that.patch
                && redhatBuild == that.redhatBuild;
    }

    @Override
    public int hashCode() {
        if (!parsed) return original.hashCode();
        return Objects.hash(major, minor, patch, redhatBuild);
    }
}
