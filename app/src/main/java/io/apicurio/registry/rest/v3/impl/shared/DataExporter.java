package io.apicurio.registry.rest.v3.impl.shared;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.utils.impexp.v3.EntityWriter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Used to export all Registry data to a .zip file.
 */
@ApplicationScoped
public class DataExporter {

    private static final String ERRORS_MANIFEST_NAME = ".errors";

    @Inject
    Logger log;

    @Inject
    @Current
    RegistryStorage storage;

    /**
     * Exports all registry data.
     */
    public Response exportData() {
        return exportData(null);
    }

    /**
     * Exports registry data, optionally filtered by group.
     *
     * @param groupId if non-null, only data belonging to this group will be exported
     */
    public Response exportData(String groupId) {
        StreamingOutput stream = os -> {
            try (ZipOutputStream zip = new ZipOutputStream(os, StandardCharsets.UTF_8)) {
                EntityWriter writer = new EntityWriter(zip);
                List<Map<String, String>> errors = new ArrayList<>();
                storage.exportData(groupId, entity -> {
                    try {
                        writer.writeEntity(entity);
                    } catch (Exception e) {
                        log.error("Error writing entity during export", e);
                        String error = e.getMessage();
                        if (error == null) {
                            error = e.getClass().getName();
                        }
                        errors.add(Map.of(
                                "entityType", entity.getEntityType().name(),
                                "error", error));
                    }
                    return null;
                });

                if (!errors.isEmpty()) {
                    zip.putNextEntry(new ZipEntry(ERRORS_MANIFEST_NAME));
                    String errorsJson = new ObjectMapper().writeValueAsString(errors);
                    zip.write(errorsJson.getBytes(StandardCharsets.UTF_8));
                    zip.closeEntry();
                }

                zip.flush();
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException("Export failed due to error writing entities", e);
            }
        };

        return Response.ok(stream).type("application/zip").build();
    }

}
