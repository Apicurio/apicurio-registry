package io.apicurio.registry.rest.v3.impl.shared;

import io.apicurio.registry.utils.impexp.Entity;

import java.io.IOException;
import java.util.zip.ZipOutputStream;

public interface ExportFormatStrategy {

    void init(ZipOutputStream zip);

    void handleEntity(Entity entity) throws IOException;

    void finish() throws IOException;
}
