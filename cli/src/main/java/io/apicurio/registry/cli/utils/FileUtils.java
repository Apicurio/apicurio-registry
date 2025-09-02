package io.apicurio.registry.cli.utils;

import io.apicurio.registry.cli.common.CliException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static io.apicurio.registry.cli.common.CliException.APPLICATION_ERROR_RETURN_CODE;
import static java.util.Comparator.reverseOrder;

public final class FileUtils {

    private static final Logger log = LogManager.getRootLogger();

    private FileUtils() {
    }

    public static void replaceInFile(Path filePath, String search, String replacement) {
        try {
            String content = Files.readString(filePath);
            String updatedContent = content.replace(search, replacement);
            Files.writeString(filePath, updatedContent);
            log.debug("Replaced '{}' with '{}' in file: {}", search, replacement, filePath);
        } catch (IOException e) {
            log.error("Failed to replace '{}' in file: {}", search, filePath, e);
            throw new CliException("Failed to replace in file: " + filePath, APPLICATION_ERROR_RETURN_CODE);
        }
    }

    public static boolean findInFile(Path filePath, String search) {
        try {
            String content = Files.readString(filePath);
            return content.contains(search);
        } catch (IOException e) {
            log.error("Failed to read file: {}", filePath, e);
            throw new CliException("Failed to read file: " + filePath, APPLICATION_ERROR_RETURN_CODE);
        }
    }

    public static void createLink(Path linkPath, Path targetPath) {
        try {
            if (Files.exists(linkPath)) {
                if (Files.isSymbolicLink(linkPath)) {
                    Path existingTarget = Files.readSymbolicLink(linkPath);
                    if (existingTarget.equals(targetPath)) {
                        log.debug("Symbolic link already exists: {} -> {}", linkPath, targetPath);
                    } else {
                        Files.delete(linkPath);
                        log.debug("Deleted existing symbolic link: {}", linkPath);
                    }
                } else {
                    throw new CliException("File exists and is not a symbolic link: " + linkPath,
                            APPLICATION_ERROR_RETURN_CODE);
                }
            } else {
                Files.createSymbolicLink(linkPath, targetPath);
                log.debug("Created symbolic link: {} -> {}", linkPath, targetPath);
            }
        } catch (IOException e) {
            throw new CliException("Failed to create symbolic link: " + linkPath,
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
            log.debug("Directory does not exist, nothing to delete: {}", dirPath);
            return;
        }
        try (var paths = Files.walk(dirPath)) {
            paths.sorted(reverseOrder()) // Reverse order to delete files before directories
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                            log.debug("Deleted: {}", path);
                        } catch (IOException e) {
                            log.error("Failed to delete: {}", path, e);
                            throw new CliException("Failed to delete: " + path, APPLICATION_ERROR_RETURN_CODE);
                        }
                    });
            log.debug("Deleted directory: {}", dirPath);
        } catch (IOException e) {
            log.error("Failed to walk directory: {}", dirPath, e);
            throw new CliException("Failed to delete directory: " + dirPath, APPLICATION_ERROR_RETURN_CODE);
        }
    }
}
