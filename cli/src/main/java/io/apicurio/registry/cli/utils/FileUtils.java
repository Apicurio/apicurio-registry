package io.apicurio.registry.cli.utils;

import io.apicurio.registry.cli.common.CliException;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static io.apicurio.registry.cli.common.CliException.APPLICATION_ERROR_RETURN_CODE;
import static java.util.Comparator.reverseOrder;

public final class FileUtils {

    private static final Logger log = Logger.getLogger(FileUtils.class);

    private FileUtils() {
    }

    public static void replaceInFile(Path filePath, String search, String replacement) {
        try {
            String content = Files.readString(filePath);
            String updatedContent = content.replace(search, replacement);
            Files.writeString(filePath, updatedContent);
            log.debugf("Replaced '%s' with '%s' in file: %s", search, replacement, filePath);
        } catch (IOException e) {
            log.errorf(e, "Failed to replace '%s' in file: %s", search, filePath);
            throw new CliException("Failed to replace in file: " + filePath, APPLICATION_ERROR_RETURN_CODE);
        }
    }

    public static boolean findInFile(Path filePath, String search) {
        try {
            String content = Files.readString(filePath);
            return content.contains(search);
        } catch (IOException e) {
            log.errorf(e, "Failed to read file: %s", filePath);
            throw new CliException("Failed to read file: " + filePath, APPLICATION_ERROR_RETURN_CODE);
        }
    }

    public static void createLink(Path linkPath, Path targetPath) {
        try {
            if (Files.exists(linkPath)) {
                if (Files.isSymbolicLink(linkPath)) {
                    Path existingTarget = Files.readSymbolicLink(linkPath);
                    if (existingTarget.equals(targetPath)) {
                        log.debugf("Symbolic link already exists: %s -> %s", linkPath, targetPath);
                    } else {
                        Files.delete(linkPath);
                        log.debugf("Deleted existing symbolic link: %s", linkPath);
                    }
                } else {
                    throw new CliException("File exists and is not a symbolic link: " + linkPath,
                            APPLICATION_ERROR_RETURN_CODE);
                }
            } else {
                Files.createSymbolicLink(linkPath, targetPath);
                log.debugf("Created symbolic link: %s -> %s", linkPath, targetPath);
            }
        } catch (IOException ex) {
            throw new CliException("Failed to create symbolic link: " + linkPath, ex,
                    APPLICATION_ERROR_RETURN_CODE);
        }
    }

    public static void unzip(Path zipFilePath, Path targetDirPath) throws IOException {
        Files.createDirectories(targetDirPath);
        try (var zis = new ZipInputStream(Files.newInputStream(zipFilePath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path outPath = targetDirPath.resolve(entry.getName()).normalize();
                if (!outPath.startsWith(targetDirPath)) {
                    throw new IOException("Zip entry outside target dir: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(outPath);
                } else {
                    Files.createDirectories(outPath.getParent());
                    try (var out = Files.newOutputStream(outPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                        zis.transferTo(out);
                    }
                }
                zis.closeEntry();
            }
        }
    }

    public static void deleteDirectory(Path dirPath) {
        if (!Files.exists(dirPath)) {
            log.debugf("Directory does not exist, nothing to delete: %s", dirPath);
            return;
        }
        try (var paths = Files.walk(dirPath)) {
            paths.sorted(reverseOrder()) // Reverse order to delete files before directories
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                            log.debugf("Deleted: %s", path);
                        } catch (IOException e) {
                            log.errorf(e, "Failed to delete: %s", path);
                            throw new CliException("Failed to delete: " + path, APPLICATION_ERROR_RETURN_CODE);
                        }
                    });
            log.debugf("Deleted directory: %s", dirPath);
        } catch (IOException e) {
            log.errorf(e, "Failed to walk directory: %s", dirPath);
            throw new CliException("Failed to delete directory: " + dirPath, APPLICATION_ERROR_RETURN_CODE);
        }
    }
}
