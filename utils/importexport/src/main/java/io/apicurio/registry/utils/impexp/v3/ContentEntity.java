package io.apicurio.registry.utils.impexp.v3;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.apicurio.registry.utils.impexp.Entity;
import io.apicurio.registry.utils.impexp.EntityType;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.Map;

import static lombok.AccessLevel.PRIVATE;

@Builder
@NoArgsConstructor
@AllArgsConstructor(access = PRIVATE)
@ToString
@RegisterForReflection
public class ContentEntity extends Entity {

    public long contentId;

    /**
     * Deprecated: Kept for backward compatibility with old export files.
     * Use 'hashes' map instead for new exports.
     */
    @Deprecated
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String canonicalHash;

    /**
     * Deprecated: Kept for backward compatibility with old export files.
     * Use 'hashes' map instead for new exports.
     */
    @Deprecated
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String contentHash;

    /**
     * Map of hash type to hash value.
     * Example: {"content-sha256": "abc123...", "canonical-sha256": "def456..."}
     * New format for supporting multiple hash types per content.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Map<String, String> hashes;

    public String artifactType;
    public String contentType;

    @JsonIgnore
    @ToString.Exclude
    public byte[] contentBytes;

    public String serializedReferences;

    /**
     * @see Entity#getEntityType()
     */
    @Override
    public EntityType getEntityType() {
        return EntityType.Content;
    }

    /**
     * Returns true if this entity uses the old hash format (individual fields).
     *
     * @return true if using old format
     */
    @JsonIgnore
    public boolean isOldHashFormat() {
        return (canonicalHash != null || contentHash != null) && (hashes == null || hashes.isEmpty());
    }

    /**
     * Returns true if this entity uses the new hash format (hashes map).
     *
     * @return true if using new format
     */
    @JsonIgnore
    public boolean isNewHashFormat() {
        return hashes != null && !hashes.isEmpty();
    }

}
