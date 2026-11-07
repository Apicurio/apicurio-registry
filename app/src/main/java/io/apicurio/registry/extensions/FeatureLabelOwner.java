package io.apicurio.registry.extensions;

import java.util.Set;

/**
 * Declares label key prefixes that a registry feature owns.
 * <p>
 * Features that persist their own metadata in labels implement this as a CDI bean. When a
 * metadata update replaces the label map of a group, artifact or version, labels whose keys
 * start with a declared prefix are kept unless the caller sends them, so an ordinary user edit
 * does not silently delete feature data. A caller that does send such a label still has its
 * value applied: this guards against accidental deletion, not against deliberate edits, so
 * labels must never hold security-relevant state (see {@code .claude/rules/labels.md}).
 * </p>
 * <p>
 * Do not declare labels that users are meant to manage, such as {@code apicurio.agent.visibility}.
 * </p>
 */
public interface FeatureLabelOwner {

    /**
     * @return the label key prefixes this feature owns, for example {@code "contract."}
     */
    Set<String> ownedLabelPrefixes();

    /**
     * @return individual label keys this feature owns that do not share a prefix, for example a
     *         legacy key kept for backwards compatibility
     */
    default Set<String> ownedLabelKeys() {
        return Set.of();
    }
}
