package io.apicurio.registry.examples.customtypes;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rules.compatibility.CompatibilityChecker;
import io.apicurio.registry.rules.compatibility.CompatibilityExecutionResult;
import io.apicurio.registry.rules.compatibility.CompatibilityLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Backs the COMPATIBILITY rule for the MARKDOWN artifact type. A new version is:
 * <ul>
 * <li>{@code BACKWARD} compatible when it keeps the title and all sections of the previous version
 * (sections may be added);</li>
 * <li>{@code FORWARD} compatible when it does not introduce new sections;</li>
 * <li>{@code FULL} compatible when both hold.</li>
 * </ul>
 * The {@code *_TRANSITIVE} levels apply the same checks against every existing version.
 */
public class MarkdownCompatibilityChecker implements CompatibilityChecker {

    @Override
    public CompatibilityExecutionResult testCompatibility(CompatibilityLevel level,
            List<TypedContent> existingArtifacts, TypedContent proposedArtifact,
            Map<String, TypedContent> resolvedReferences) {
        if (level == null || level == CompatibilityLevel.NONE || existingArtifacts == null
                || existingArtifacts.isEmpty()) {
            return CompatibilityExecutionResult.compatible();
        }

        boolean backward = level.name().startsWith("BACKWARD") || level.name().startsWith("FULL");
        boolean forward = level.name().startsWith("FORWARD") || level.name().startsWith("FULL");

        String proposed = Markdown.text(proposedArtifact);
        String proposedTitle = Markdown.title(proposed);
        Set<String> proposedSections = Markdown.sections(proposed);

        List<String> problems = new ArrayList<>();
        for (TypedContent existingContent : versionsToCompare(level, existingArtifacts)) {
            String existing = Markdown.text(existingContent);
            problems.addAll(checkTitleCompatibility(existing, proposedTitle));
            problems.addAll(checkSectionCompatibility(existing, proposedSections, backward, forward));
        }

        return problems.isEmpty() ? CompatibilityExecutionResult.compatible()
            : CompatibilityExecutionResult.incompatible(String.join(" ", problems));
    }

    private List<TypedContent> versionsToCompare(CompatibilityLevel level,
            List<TypedContent> existingArtifacts) {
        return level.name().endsWith("_TRANSITIVE")
            ? existingArtifacts
            : List.of(existingArtifacts.get(existingArtifacts.size() - 1));
    }

    private List<String> checkTitleCompatibility(String existing, String proposedTitle) {
        if (Objects.equals(Markdown.title(existing), proposedTitle)) {
            return List.of();
        }
        return List.of("Title changed from \"" + Markdown.title(existing) + "\" to \"" + proposedTitle + "\".");
    }

    private List<String> checkSectionCompatibility(String existing, Set<String> proposedSections,
            boolean backward, boolean forward) {
        Set<String> existingSections = Markdown.sections(existing);
        List<String> problems = new ArrayList<>();

        if (backward) {
            for (String section : existingSections) {
                if (!proposedSections.contains(section)) {
                    problems.add("Section \"" + section + "\" was removed.");
                }
            }
        }

        if (forward) {
            for (String section : proposedSections) {
                if (!existingSections.contains(section)) {
                    problems.add("Section \"" + section + "\" was added.");
                }
            }
        }

        return problems;
    }
}
