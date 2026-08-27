/*
 * Copyright 2026 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.rest.v3.impl.shared;

import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.error.RegistryStorageException;
import io.apicurio.registry.utils.impexp.Entity;
import io.apicurio.registry.utils.impexp.EntityType;
import io.apicurio.registry.utils.impexp.ManifestEntity;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for DataExporter.
 */
class DataExporterTest {

    /**
     * The export writes into a ZipOutputStream wrapping the response stream. When the storage layer
     * fails part way through the export, that zip stream still needs to be closed, otherwise its
     * Deflater and the underlying response stream are leaked.
     */
    @Test
    void testExportData_ClosesZipStreamWhenStorageFails() {
        RegistryStorage storage = mock(RegistryStorage.class);
        doThrow(new RegistryStorageException("Simulated storage failure")).when(storage)
                .exportData(any(), any());

        DataExporter exporter = new DataExporter();
        exporter.log = LoggerFactory.getLogger(DataExporter.class);
        exporter.storage = storage;

        StreamingOutput stream = (StreamingOutput) exporter.exportData(null).getEntity();
        CloseTrackingOutputStream output = new CloseTrackingOutputStream();

        // When: the export fails while streaming
        IOException thrown = assertThrows(IOException.class, () -> stream.write(output));
        assertInstanceOf(RegistryStorageException.class, thrown.getCause());

        // Then: the zip stream, and with it the response stream, was still closed
        assertTrue(output.closed, "Expected the ZipOutputStream to be closed after a failed export");
    }

    @Test
    void exportDataShouldIncludeErrorsManifestForPartiallyFailedExport() throws IOException {
        DataExporter exporter = new DataExporter();

        exporter.log = LoggerFactory.getLogger(DataExporter.class);

        RegistryStorage storage = mock(RegistryStorage.class);
        doAnswer(invocation -> {
            Function<Entity, Void> handler = invocation.getArgument(1);
            handler.apply(new ManifestEntity());
            Entity badEntity = mock(Entity.class);
            when(badEntity.getEntityType()).thenReturn(EntityType.Content);
            handler.apply(badEntity);
            return null;
        }).when(storage).exportData(any(), any());

        exporter.storage = storage;

        Response response = exporter.exportData();
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus(),
                "The response is prepared with 200 before the stream is written");

        StreamingOutput stream = (StreamingOutput) response.getEntity();
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        stream.write(os);

        ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(os.toByteArray()));
        boolean foundErrors = false;
        String errorsContent = null;
        ZipEntry entry;
        while ((entry = zis.getNextEntry()) != null) {
            if (".errors".equals(entry.getName())) {
                foundErrors = true;
                errorsContent = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                break;
            }
        }
        assertTrue(foundErrors, "Expected .errors manifest to be present in a partially failed export");
        assertTrue(errorsContent.contains(EntityType.Content.name()),
                "The .errors manifest should reference the failed entity type");
        assertTrue(errorsContent.contains("ClassCastException"),
                "The .errors manifest should include the error details");
    }

    private static class CloseTrackingOutputStream extends OutputStream {

        boolean closed;

        @Override
        public void write(int b) {
            // Discarded this test only cares about whether close() was called.
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }
}
