package io.apicurio.registry.rest.v3.impl.shared;

import io.apicurio.registry.rest.v3.impl.shared.gitops.GitOpsEntityCollector;
import io.apicurio.registry.rest.v3.impl.shared.gitops.GitOpsZipWriter;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.utils.impexp.v3.EntityWriter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipOutputStream;

/**
 * Used to export all Registry data to a .zip file.
 */
@ApplicationScoped
public class DataExporter {

    @Inject
    Logger log;

    @Inject
    @Current
    RegistryStorage storage;

    public Response exportData() {
        return exportData(null, null);
    }

    public Response exportData(String groupId) {
        return exportData(groupId, null);
    }

    public Response exportData(String groupId, String format) {
        if ("gitops-v1".equals(format)) {
            return exportGitOpsData(groupId);
        }
        return exportDefaultData(groupId);
    }

    private Response exportDefaultData(String groupId) {
        final StreamingOutput stream = os -> {
            try (ZipOutputStream zip = new ZipOutputStream(os, StandardCharsets.UTF_8)) {
                final EntityWriter writer = new EntityWriter(zip);
                storage.exportData(groupId, entity -> {
                    try {
                        writer.writeEntity(entity);
                    } catch (Exception e) {
                        log.error("Error writing entity during export", e);
                        throw new RuntimeException("Error writing entity during export", e);
                    }
                    return null;
                });

                zip.flush();
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException("Export failed due to error writing entities", e);
            }
        };

        return Response.ok(stream).type("application/zip").build();
    }

    private Response exportGitOpsData(String groupId) {
        final StreamingOutput stream = os -> {
            try (ZipOutputStream zip = new ZipOutputStream(os, StandardCharsets.UTF_8)) {
                final GitOpsEntityCollector collector = new GitOpsEntityCollector();
                storage.exportData(groupId, entity -> {
                    collector.collect(entity);
                    return null;
                });

                for (final String warning : collector.getWarnings()) {
                    log.warn(warning);
                }

                final GitOpsZipWriter writer = new GitOpsZipWriter(zip, collector);
                writer.write();
                zip.flush();
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException("GitOps export failed due to error writing entities", e);
            }
        };

        return Response.ok(stream).type("application/zip").build();
    }
}
