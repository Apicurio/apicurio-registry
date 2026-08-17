package io.apicurio.registry.operator.it;

import lombok.Getter;
import org.semver4j.Semver;

import java.util.List;
import java.util.Map;

import static io.apicurio.registry.operator.it.OLMTestUtils.PACKAGE_NAME;

/**
 * Holds discovered catalog metadata from the live PackageManifest. Provides query methods to determine
 * channel heads, previous versions for upgrade testing, and whether the current version is in a channel.
 */
@Getter
public class CatalogInfo {

    private final Map<String, List<ChannelEntry>> channels;
    private final String defaultChannel;

    CatalogInfo(Map<String, List<ChannelEntry>> channels, String defaultChannel) {
        this.channels = channels;
        this.defaultChannel = defaultChannel;
    }

    @Getter
    public static class ChannelEntry {

        private final String csvName;
        private final Semver version;

        ChannelEntry(String csvName, Semver version) {
            this.csvName = csvName;
            this.version = version;
        }
    }

    public boolean hasChannel(String channelName) {
        return channels.containsKey(channelName);
    }

    /**
     * Returns the channel head CSV name, or null if the channel doesn't exist.
     */
    public String getChannelHeadCSV(String channelName) {
        var head = getChannelHead(channelName);
        return head != null ? head.getCsvName() : null;
    }

    /**
     * Returns the version of the channel head.
     */
    public Semver getChannelHeadVersion(String channelName) {
        var head = getChannelHead(channelName);
        return head != null ? head.getVersion() : null;
    }

    /**
     * Returns the head entry, or null if the channel doesn't exist or is empty.
     */
    public ChannelEntry getChannelHead(String channelName) {
        var entries = channels.get(channelName);
        return (entries != null && !entries.isEmpty()) ? entries.get(0) : null;
    }

    /**
     * Returns a suitable "previous" entry for upgrade testing — the second entry in the channel
     * (i.e., the one immediately before the head). Returns null if the channel has fewer than 2 entries.
     */
    public ChannelEntry getPreviousEntry(String channelName) {
        var entries = channels.get(channelName);
        if (entries == null || entries.size() < 2) {
            return null;
        }
        return entries.get(1);
    }

    /**
     * Returns a previous entry from a different minor version than the channel head, for cross-minor
     * upgrade testing. Returns null if no such entry exists.
     */
    public ChannelEntry getCrossMinorEntry(String channelName) {
        var entries = channels.get(channelName);
        if (entries == null || entries.size() < 2) {
            return null;
        }
        var headMinor = getChannelHeadVersion(channelName).getMinor();
        for (int i = 1; i < entries.size(); i++) {
            if (entries.get(i).getVersion().getMinor() != headMinor) {
                return entries.get(i);
            }
        }
        return null;
    }

    /**
     * Finds the channel with the highest minor version that is strictly less than the current version's
     * minor, excluding the rolling channel. Returns null if no such channel exists.
     */
    public String getOlderMinorChannel(String currentVersion, String rollingChannel) {
        var currentMinor = coerceVersion(currentVersion).getMinor();
        String best = null;
        int bestMinor = -1;
        for (var ch : channels.keySet()) {
            if (ch.equals(rollingChannel)) {
                continue;
            }
            var head = getChannelHeadVersion(ch);
            if (head != null && head.getMinor() < currentMinor && head.getMinor() > bestMinor) {
                bestMinor = head.getMinor();
                best = ch;
            }
        }
        return best;
    }

    /**
     * Checks if the given version is the head of the given channel. Uses semver comparison
     * on major.minor.patch, tolerating downstream pre-release suffixes.
     */
    public boolean isVersionChannelHead(String channelName, String version) {
        var headVersion = getChannelHeadVersion(channelName);
        if (headVersion == null) {
            return false;
        }
        var target = coerceVersion(version);
        return headVersion.getMajor() == target.getMajor()
                && headVersion.getMinor() == target.getMinor()
                && headVersion.getPatch() == target.getPatch();
    }

    /**
     * Extracts the version string from a CSV name by stripping the package prefix.
     * E.g., "apicurio-registry-3.v3.2.4-r1" -> "3.2.4-r1"
     */
    public static String extractVersionString(String csvName) {
        var prefix = PACKAGE_NAME + ".v";
        if (csvName != null && csvName.startsWith(prefix)) {
            return csvName.substring(prefix.length());
        }
        return csvName;
    }

    /**
     * Coerces a version string into a Semver, tolerating downstream suffixes.
     */
    static Semver coerceVersion(String version) {
        var semver = Semver.coerce(version);
        if (semver == null) {
            throw new IllegalArgumentException("Cannot parse version: " + version);
        }
        return semver;
    }
}
