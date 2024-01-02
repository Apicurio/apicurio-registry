package io.apicurio.registry.rest.v2.shared;

import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.types.Current;
import io.apicurio.registry.utils.impexp.EntityWriter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipOutputStream;

@ApplicationScoped
public class DataExporter {

    @Inject
    Logger log;

    @Inject
    @Current
    RegistryStorage storage;

    /**
     * Exports all registry data.
     */
    public Response exportData() {
        StreamingOutput stream = os -> {
            try {
                ZipOutputStream zip = new ZipOutputStream(os, StandardCharsets.UTF_8);
                EntityWriter writer = new EntityWriter(zip);
                AtomicInteger errorCounter = new AtomicInteger(0);
                storage.exportData(entity -> {
                    try {
                        writer.writeEntity(entity);
                    } catch (Exception e) {
                        // TODO do something interesting with this
                        e.printStackTrace();
                        errorCounter.incrementAndGet();
                    }
                    return null;
                });

                // TODO if the errorCounter > 0, then what?

                zip.flush();
                zip.close();
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException(e);
            }
        };

        return Response.ok(stream).type("application/zip").build();
    }

}
