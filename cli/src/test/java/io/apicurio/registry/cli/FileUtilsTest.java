package io.apicurio.registry.cli;

import io.apicurio.registry.cli.common.CliException;
import io.apicurio.registry.cli.utils.FileUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledOnOs({OS.LINUX, OS.MAC})
class FileUtilsTest {

    @TempDir
    Path directory;

    @Test
    void testCreateNewLink() throws IOException {
        Path target = Files.writeString(directory.resolve("target"), "launcher");
        Path link = directory.resolve("acr");

        FileUtils.createLink(link, target);

        assertThat(Files.readSymbolicLink(link)).isEqualTo(target);
        assertThat(Files.readString(link)).isEqualTo("launcher");
    }

    @Test
    void testExistingLinkToSameTarget() throws IOException {
        Path target = Files.writeString(directory.resolve("target"), "launcher");
        Path link = Files.createSymbolicLink(directory.resolve("acr"), target);

        FileUtils.createLink(link, target);

        assertThat(Files.readSymbolicLink(link)).isEqualTo(target);
        assertThat(Files.readString(link)).isEqualTo("launcher");
    }

    @Test
    void testReplaceLinkToDifferentTarget() throws IOException {
        Path oldTarget = Files.writeString(directory.resolve("old-target"), "old launcher");
        Path newTarget = Files.writeString(directory.resolve("new-target"), "new launcher");
        Path link = Files.createSymbolicLink(directory.resolve("acr"), oldTarget);

        FileUtils.createLink(link, newTarget);

        assertThat(Files.readSymbolicLink(link)).isEqualTo(newTarget);
        assertThat(Files.readString(link)).isEqualTo("new launcher");
        assertThat(Files.readString(oldTarget)).isEqualTo("old launcher");
    }

    @Test
    void testReplaceDanglingLink() throws IOException {
        Path newTarget = Files.writeString(directory.resolve("new-target"), "new launcher");
        Path link = Files.createSymbolicLink(directory.resolve("acr"), directory.resolve("missing-target"));

        FileUtils.createLink(link, newTarget);

        assertThat(Files.readSymbolicLink(link)).isEqualTo(newTarget);
        assertThat(Files.readString(link)).isEqualTo("new launcher");
    }

    @Test
    void testExistingRegularFileIsPreserved() throws IOException {
        Path target = Files.writeString(directory.resolve("target"), "launcher");
        Path link = Files.writeString(directory.resolve("acr"), "user file");

        assertThatThrownBy(() -> FileUtils.createLink(link, target))
                .isInstanceOf(CliException.class)
                .hasMessage("File exists and is not a symbolic link: " + link);
        assertThat(Files.isSymbolicLink(link)).isFalse();
        assertThat(Files.readString(link)).isEqualTo("user file");
        assertThat(Files.readString(target)).isEqualTo("launcher");
    }
}
