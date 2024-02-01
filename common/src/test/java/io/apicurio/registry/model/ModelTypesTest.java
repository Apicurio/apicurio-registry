package io.apicurio.registry.model;


import jakarta.validation.ValidationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ModelTypesTest {


    @Test
    void testGroupId() {
        assertTrue(GroupId.DEFAULT.isDefaultGroup());

        assertEquals(GroupId.DEFAULT, new GroupId(null));
        assertEquals(GroupId.DEFAULT, new GroupId("default"));
        assertEquals(null, GroupId.DEFAULT.getRawGroupIdWithNull());
        assertEquals("default", GroupId.DEFAULT.getRawGroupIdWithDefaultString());

        assertThrows(ValidationException.class, () -> new GroupId(""));
        assertThrows(ValidationException.class, () -> new GroupId("x".repeat(513)));

        new GroupId("x");
        new GroupId("._@# $%");
        new GroupId("x".repeat(512));
    }


    @Test
    void testArtifactId() {
        assertThrows(ValidationException.class, () -> new ArtifactId(null));
        assertThrows(ValidationException.class, () -> new ArtifactId(""));
        assertThrows(ValidationException.class, () -> new ArtifactId("x".repeat(513)));

        new ArtifactId("x");
        new ArtifactId("._@# $%");
        new ArtifactId("x".repeat(512));
    }


    @Test
    void testVersionId() {
        assertThrows(ValidationException.class, () -> new VersionId(null));
        assertThrows(ValidationException.class, () -> new VersionId(""));
        assertThrows(ValidationException.class, () -> new VersionId(" "));
        assertThrows(ValidationException.class, () -> new VersionId("="));
        assertThrows(ValidationException.class, () -> new VersionId("x".repeat(257)));

        new VersionId("x");
        new VersionId("._-+");
        new VersionId("x".repeat(256));
    }


    @Test
    void testBranchId() {
        assertEquals(BranchId.LATEST, new BranchId("latest"));

        assertThrows(ValidationException.class, () -> new BranchId(null));
        assertThrows(ValidationException.class, () -> new BranchId(""));
        assertThrows(ValidationException.class, () -> new BranchId(" "));
        assertThrows(ValidationException.class, () -> new BranchId("="));
        assertThrows(ValidationException.class, () -> new BranchId("x".repeat(257)));

        new BranchId("x");
        new BranchId("._-+");
        new BranchId("x".repeat(256));
    }


    @Test
    void testGAandGAV() {
        var ga1 = new GA(null, "artifact1");
        var ga2 = new GA("default", "artifact1");
        assertEquals(ga1, ga2);

        var gav1 = new GAV(null, "artifact1", "version1");
        var gav2 = new GAV(ga2, new VersionId("version1"));
        assertEquals(gav1, gav2);

        assertNotEquals(ga1, gav1);
        assertNotEquals(gav1, ga1);
    }
}
