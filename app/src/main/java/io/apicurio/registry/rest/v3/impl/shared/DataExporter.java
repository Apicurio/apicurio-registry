package io.apicurio.registry.rest.v3.impl.shared;

import io.apicurio.registry.rest.v3.impl.shared.gitops.GitOpsExportStrategy;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.cdi.Current;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipOutputStream;

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
        final ExportFormatStrategy strategy = resolveStrategy(format);
        return doExport(groupId, strategy);
    }

    private ExportFormatStrategy resolveStrategy(String format) {
        if ("gitops-v0".equals(format)) {
            return new GitOpsExportStrategy(log);
        }
        return new DefaultExportStrategy();
    }

    private Response doExport(String groupId, ExportFormatStrategy strategy) {
        final StreamingOutput stream = os -> {
            try (final ZipOutputStream zip = new ZipOutputStream(os, StandardCharsets.UTF_8)) {
                strategy.init(zip);
                storage.exportData(groupId, entity -> {
                    writeEntity(strategy, entity);
                    return null;
                });
                strategy.finish();
                zip.flush();
            } catch (RuntimeException e) {
                throw unwrapIOException(e);
            }
        };

        return Response.ok(stream).type("application/zip").build();
    }

    private void writeEntity(ExportFormatStrategy strategy,
            io.apicurio.registry.utils.impexp.Entity entity) {
        try {
            strategy.handleEntity(entity);
        } catch (IOException e) {
            log.error("Error writing entity during export", e);
            throw new RuntimeException(e);
        }
    }

    private static IOException unwrapIOException(RuntimeException e) {
        final Throwable cause = e.getCause();
        if (cause instanceof IOException ioe) {
            return ioe;
        }
        final Throwable rootCause = cause != null ? cause : e;
        return new IOException("Export failed", rootCause);
    }
}
