package io.apicurio.common.apps.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Mojo(name = "verify-dependencies")
public class VerifyDependenciesMojo extends AbstractMojo {

    @Parameter
    List<String> fileTypes;

    @Parameter
    List<File> directories;

    @Parameter
    List<File> distributions;

    /**
     * @see org.apache.maven.plugin.Mojo#execute()
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            if (fileTypes == null) {
                fileTypes = List.of("jar");
            }
            if (directories == null) {
                directories = List.of();
            }
            if (distributions == null) {
                distributions = List.of();
            }

            getLog().info("Verifying dependencies in " + directories.size() + " directories");

            // Find the files we want to validate.
            Set<String> filesToValidate = new TreeSet<>();

            // Find files in configured directories.
            for (File directory : directories) {
                if (!directory.isDirectory()) {
                    throw new MojoFailureException("Configured directory is not a directory: " + directory);
                }
                Path dirPath = directory.getCanonicalFile().toPath();

                filesToValidate.addAll(Files.list(dirPath).filter(Files::isRegularFile)
                        .filter(file -> isDependencyJarFile(file.toString()))
                        .map(file -> dirPath.toString() + "::" + file.getFileName())
                        .collect(Collectors.toSet()));
            }

            if (filesToValidate.isEmpty()) {
                throw new MojoFailureException("Found 0 dependencies (from configured sources) to verify!");
            }

            // Find files in configured distributions.
            for (File distribution : distributions) {
                filesToValidate.addAll(findAllInZip(distribution));
            }

            // Validate those files.
            Set<String> invalidArtifacts = new TreeSet<>();
            filesToValidate.forEach(file -> {
                if (!isValid(file)) {
                    invalidArtifacts.add(file);
                }
            });

            if (!invalidArtifacts.isEmpty()) {
                String serializedInvalidArtifacts = serialize(invalidArtifacts);
                throw new MojoFailureException("Invalid dependencies found: \n" + serializedInvalidArtifacts);
            }
        } catch (MojoFailureException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException("Cannot build project dependency graph", e);
        }
    }

    private Set<String> findAllInZip(File distribution) throws IOException {
        Set<String> foundFiles = new TreeSet<>();

        try (ZipFile zipFile = new ZipFile(distribution)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.isDirectory()) {
                    String entryName = entry.getName();
                    if (isDependencyJarFile(entryName)) {
                        foundFiles.add(distribution.getName() + "::" + entryName);
                    }
                }
            }
        }

        return foundFiles;
    }

    protected boolean isDependencyJarFile(String file) {
        String fname = file;
        int dotIdx = fname.lastIndexOf('.');
        String extension = fname.substring(dotIdx + 1);
        return fileTypes.indexOf(extension) != -1;
    }

    private boolean isValid(String artifactPath) {
        return artifactPath.contains("-redhat-") || artifactPath.contains(".redhat-");
    }

    private static String serialize(Set<String> invalidArtifacts) {
        StringBuilder sb = new StringBuilder();
        for (String artifact : invalidArtifacts) {
            sb.append("    ");
            sb.append(artifact);
            sb.append("\n");
        }
        return sb.toString();
    }

}
