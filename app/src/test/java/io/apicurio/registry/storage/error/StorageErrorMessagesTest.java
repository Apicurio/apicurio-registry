package io.apicurio.registry.storage.error;

import io.apicurio.registry.model.GroupId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StorageErrorMessagesTest {

    @Test
    void defaultGroupIsNamedDefault() {
        assertEquals("No artifact with ID 'a' in group 'default' was found.",
                new ArtifactNotFoundException(null, "a").getMessage());
        assertEquals("No artifact with ID 'a' in group 'default' was found.",
                new ArtifactNotFoundException(GroupId.DEFAULT.getRawGroupId(), "a").getMessage());
        assertEquals("An artifact with ID 'a' in group 'default' already exists.",
                new ArtifactAlreadyExistsException(null, "a").getMessage());
        assertEquals("No version '1' found for artifact with ID 'a' in group 'default'.",
                new VersionNotFoundException(null, "a", "1").getMessage());
        assertEquals("Version '1' (for artifact ID 'a' in group 'default') already exists in branch 'b'.",
                new VersionAlreadyExistsOnBranchException(null, "a", "1", "b").getMessage());
    }

    @Test
    void versionAlreadyExists() {
        assertEquals("An artifact version '1' for artifact ID 'a' in group 'default' already exists.",
                new VersionAlreadyExistsException(null, "a", "1").getMessage());
        assertEquals("An artifact version '1' for artifact ID 'a' in group 'default' already exists.",
                new VersionAlreadyExistsException(GroupId.DEFAULT.getRawGroupId(), "a", "1").getMessage());
        assertEquals("An artifact version '1' for artifact ID 'a' in group 'orders' already exists.",
                new VersionAlreadyExistsException("orders", "a", "1").getMessage());
    }

    @Test
    void otherGroupsAreUnchanged() {
        assertEquals("No artifact with ID 'a' in group 'orders' was found.",
                new ArtifactNotFoundException("orders", "a").getMessage());
        assertEquals("Version '1' (for artifact ID 'a' in group 'orders') already exists in branch 'b'.",
                new VersionAlreadyExistsOnBranchException("orders", "a", "1", "b").getMessage());
    }

    @Test
    void displayNameNeverThrows() {
        assertEquals("default", GroupId.displayName(null));
        assertEquals("default", GroupId.displayName(GroupId.DEFAULT.getRawGroupId()));
        assertEquals("orders", GroupId.displayName("orders"));
        assertEquals("", GroupId.displayName(""));
    }
}
