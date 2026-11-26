package io.apicurio.registry.rest.v3.impl.shared;

import io.apicurio.registry.utils.impexp.Entity;
import io.apicurio.registry.utils.impexp.v3.EntityWriter;

import java.io.IOException;
import java.util.zip.ZipOutputStream;

public class DefaultExportStrategy implements ExportFormatStrategy {

    private EntityWriter writer;

    @Override
    public void init(ZipOutputStream zip) {
        this.writer = new EntityWriter(zip);
    }

    @Override
    public void handleEntity(Entity entity) throws IOException {
        writer.writeEntity(entity);
    }

    @Override
    public void finish() throws IOException {
        // EntityWriter writes each entity directly to the zip — no deferred work to finalize.
    }
}
