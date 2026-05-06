package io.apicurio.registry.cli.services;

import java.util.List;

public record UpdateCheckResult(
        CliVersion currentVersion,
        List<CliVersion> candidates
) {

    public boolean hasUpdates() {
        return !candidates.isEmpty();
    }

    public CliVersion patchUpdate() {
        return candidates.stream()
                .filter(v -> v.isParsed() && v.sameMajorMinor(currentVersion))
                .max(CliVersion.COMPARATOR)
                .orElse(null);
    }

    public boolean isAmbiguous() {
        if (candidates.size() <= 1) {
            return false;
        }
        return patchUpdate() == null;
    }

    public CliVersion unambiguousUpdate() {
        if (candidates.size() == 1) {
            return candidates.get(0);
        }
        return patchUpdate();
    }

    public void formatMessage(StringBuilder out) {
        for (var v : candidates) {
            var label = v.isParsed() && v.sameMajorMinor(currentVersion) ? "patch" : "minor";
            if (candidates.size() == 1) {
                out.append("  ").append(v.toString())
                        .append(" (").append(label).append(")")
                        .append("  — run 'acr update'\n");
            } else {
                out.append("  ").append(v.toString())
                        .append(" (").append(label).append(")")
                        .append("  — run 'acr update ").append(v.toString()).append("'\n");
            }
        }
    }
}
