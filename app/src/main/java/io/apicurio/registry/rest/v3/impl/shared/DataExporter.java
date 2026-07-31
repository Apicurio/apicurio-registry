package io.apicurio.registry.rest.v3.impl.shared;

import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.utils.impexp.v3.EntityWriter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipOutputStream;

/**
 * Used to export all Registry data to a .zip file.
 */
@ApplicationScoped
public class DataExporter {

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
                storage.exportData(groupId, entity -> {
                    try {
                        writer.writeEntity(entity);
                    } catch (IOException e) {
                        throw new UncheckedIOException("Error writing entity", e);
                    }
                    return null;
                });
                zip.flush();
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException(e);
            }
        };

        return Response.ok(stream).type("application/zip").build();
    }

}
