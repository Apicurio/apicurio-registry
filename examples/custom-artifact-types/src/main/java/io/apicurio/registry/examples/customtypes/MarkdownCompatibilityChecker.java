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
        boolean transitive = level.name().endsWith("_TRANSITIVE");
        boolean backward = level.name().startsWith("BACKWARD") || level.name().startsWith("FULL");
        boolean forward = level.name().startsWith("FORWARD") || level.name().startsWith("FULL");

        List<TypedContent> toCompare = transitive ? existingArtifacts
            : List.of(existingArtifacts.get(existingArtifacts.size() - 1));
        String proposed = Markdown.text(proposedArtifact);
        String proposedTitle = Markdown.title(proposed);
        Set<String> proposedSections = Markdown.sections(proposed);

        List<String> problems = new ArrayList<>();
        for (TypedContent existingContent : toCompare) {
            String existing = Markdown.text(existingContent);
            if (!Objects.equals(Markdown.title(existing), proposedTitle)) {
                problems.add("Title changed from \"" + Markdown.title(existing) + "\" to \"" + proposedTitle + "\".");
            }
            Set<String> existingSections = Markdown.sections(existing);
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
        }
        return problems.isEmpty() ? CompatibilityExecutionResult.compatible()
            : CompatibilityExecutionResult.incompatible(String.join(" ", problems));
    }
}
