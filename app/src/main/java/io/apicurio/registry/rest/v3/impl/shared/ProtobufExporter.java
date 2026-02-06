package io.apicurio.registry.rest.v3.impl.shared;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.dto.StoredArtifactVersionDto;
import io.apicurio.registry.utils.protobuf.schema.ProtoContent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Exports Protobuf artifacts with their transitive dependencies as a ZIP file.
 * Files are organized by their package names to match the expected directory structure
 * for protoc compilation.
 */
@ApplicationScoped
public class ProtobufExporter {

    @Inject
    Logger log;

    @Inject
    @Current
    RegistryStorage storage;

    /**
     * Exports a Protobuf artifact version with all its transitive dependencies as a ZIP file.
     * The ZIP structure matches the package declarations in the proto files.
     *
     * @param groupId The group ID of the artifact
     * @param artifactId The artifact ID
     * @param version The version to export
     * @return Response containing the ZIP file
     */
    public Response exportVersionAsZip(String groupId, String artifactId, String version) {
        StreamingOutput stream = os -> {
            try (ZipOutputStream zip = new ZipOutputStream(os, StandardCharsets.UTF_8)) {
                // Collect all proto files with their content
                Map<String, ProtoContent> protoFiles = collectAllProtoFiles(groupId, artifactId, version);

                // Build a mapping from original import paths to canonical paths
                Map<String, String> importPathMapping = buildImportPathMapping(protoFiles);

                // Write all files to the ZIP with fixed imports
                Set<String> writtenPaths = new HashSet<>();
                for (ProtoContent protoContent : protoFiles.values()) {
                    // Fix all imports to use canonical package-based paths
                    for (Map.Entry<String, String> mapping : importPathMapping.entrySet()) {
                        if (!mapping.getKey().equals(mapping.getValue())) {
                            protoContent.fixImport(mapping.getKey(), mapping.getValue());
                        }
                    }

                    String zipPath = protoContent.getExpectedImportPath();

                    // Avoid duplicate entries
                    if (writtenPaths.contains(zipPath)) {
                        log.warn("Skipping duplicate path in ZIP: {}", zipPath);
                        continue;
                    }
                    writtenPaths.add(zipPath);

                    ZipEntry entry = new ZipEntry(zipPath);
                    zip.putNextEntry(entry);
                    zip.write(protoContent.getContent().getBytes(StandardCharsets.UTF_8));
                    zip.closeEntry();
                }

                zip.flush();
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException("Error exporting Protobuf artifact", e);
            }
        };

        String filename = artifactId + "-" + version + ".zip";
        return Response.ok(stream)
                .type("application/zip")
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .build();
    }

    /**
     * Collects the main artifact and all its transitive dependencies.
     *
     * @return Map from original import path to ProtoContent
     */
    private Map<String, ProtoContent> collectAllProtoFiles(String groupId, String artifactId, String version) {
        Map<String, ProtoContent> result = new LinkedHashMap<>();
        Set<String> visited = new HashSet<>();

        // Get the main artifact
        StoredArtifactVersionDto mainArtifact = storage.getArtifactVersionContent(groupId, artifactId, version);
        String mainContent = mainArtifact.getContent().content();

        // Use artifact ID as the filename for the main artifact
        String mainFilename = extractFilename(artifactId);
        ProtoContent mainProto = new ProtoContent(mainFilename, mainContent);
        result.put(mainFilename, mainProto);

        // Mark as visited using coordinates
        String mainKey = buildVisitedKey(groupId, artifactId, version);
        visited.add(mainKey);

        // Recursively collect all references
        collectReferences(mainArtifact.getReferences(), result, visited);

        return result;
    }

    /**
     * Recursively collects all references.
     */
    private void collectReferences(List<ArtifactReferenceDto> references,
                                   Map<String, ProtoContent> result,
                                   Set<String> visited) {
        if (references == null || references.isEmpty()) {
            return;
        }

        for (ArtifactReferenceDto ref : references) {
            String key = buildVisitedKey(ref.getGroupId(), ref.getArtifactId(), ref.getVersion());

            if (visited.contains(key)) {
                continue;
            }
            visited.add(key);

            try {
                ContentWrapperDto content = storage.getContentByReference(ref);
                if (content != null && content.getContent() != null) {
                    String refContent = content.getContent().content();
                    // Use the reference name as the import path
                    String importPath = ref.getName();
                    ProtoContent protoContent = new ProtoContent(importPath, refContent);
                    result.put(importPath, protoContent);

                    // Recursively collect nested references
                    collectReferences(content.getReferences(), result, visited);
                }
            } catch (Exception e) {
                log.warn("Could not resolve reference: {} - {}", ref.getName(), e.getMessage());
            }
        }
    }

    /**
     * Builds a mapping from original import paths to canonical package-based paths.
     */
    private Map<String, String> buildImportPathMapping(Map<String, ProtoContent> protoFiles) {
        Map<String, String> mapping = new HashMap<>();

        for (Map.Entry<String, ProtoContent> entry : protoFiles.entrySet()) {
            String originalPath = entry.getKey();
            ProtoContent protoContent = entry.getValue();
            String canonicalPath = protoContent.getExpectedImportPath();
            mapping.put(originalPath, canonicalPath);
        }

        return mapping;
    }

    private String buildVisitedKey(String groupId, String artifactId, String version) {
        return (groupId != null ? groupId : "default") + ":" + artifactId + ":" + version;
    }

    private String extractFilename(String artifactId) {
        // If the artifact ID already ends with .proto, use it as-is
        if (artifactId.endsWith(".proto")) {
            return artifactId;
        }
        // Otherwise, append .proto
        return artifactId + ".proto";
    }
}
