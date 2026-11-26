package io.apicurio.registry.storage.impl.sql.upgrader;

import io.apicurio.registry.storage.impl.sql.jdb.Handle;
import io.apicurio.registry.types.ArtifactType;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Re-indexes the structured content of {@code AGENT_CARD} artifacts only, so that elements added to the
 * Agent Card extraction after the first backfill (the {@code kid} each signature claims, kind
 * {@code signer}) are indexed for Agent Cards stored before. Other artifact types and their rows are left
 * as they are. Like {@link StructuredContentUpgrader}, it reads and parses the latest published version of
 * every Agent Card once, and can be run again safely.
 */
@RegisterForReflection
public class AgentCardStructuredContentUpgrader extends StructuredContentUpgrader {

    @Override
    protected boolean includes(String artifactType) {
        return ArtifactType.AGENT_CARD.equals(artifactType);
    }

    @Override
    protected void clearIndex(Handle handle) {
        handle.createUpdate("DELETE FROM artifact_structured_content WHERE EXISTS (SELECT 1 FROM artifacts a "
                + "WHERE a.groupId = artifact_structured_content.groupId "
                + "AND a.artifactId = artifact_structured_content.artifactId AND a.type = ?)")
                .bind(0, ArtifactType.AGENT_CARD).execute();
    }
}
