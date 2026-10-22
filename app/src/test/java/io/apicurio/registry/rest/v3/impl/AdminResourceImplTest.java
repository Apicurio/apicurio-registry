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

package io.apicurio.registry.rest.v3.impl;

import io.apicurio.registry.storage.importing.ImportExportConfigProperties;
import jakarta.ws.rs.BadRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for AdminResourceImpl.
 */
class AdminResourceImplTest {

    /**
     * The import unpacks the uploaded zip through IoUtil.unpackToDisk, which rejects archives that
     * exceed the configured limits. That rejection fires on untrusted input, so the ZipInputStream
     * still needs to be closed when it happens, otherwise it leaks along with its Inflater.
     */
    @Test
    void testImportData_ClosesZipStreamWhenEntryLimitExceeded(@TempDir Path workDir) throws Exception {
        ImportExportConfigProperties props = new ImportExportConfigProperties();
        props.workDir = workDir.toString();
        props.zipMaxEntrySize = 1024;
        props.zipMaxTotalSize = 1024;
        props.zipMaxEntryCount = 1;

        AdminResourceImpl resource = new AdminResourceImpl();
        resource.importExportProps = props;

        CloseTrackingInputStream data = new CloseTrackingInputStream(
                new ByteArrayInputStream(zipWithTwoEntries()));

        // When: the archive holds more entries than the configured limit allows
        assertThrows(BadRequestException.class, () -> resource.importData(null, null, false, data));

        // Then: the ZipInputStream, and with it the uploaded stream, was still closed
        assertTrue(data.closed, "Expected the ZipInputStream to be closed after a rejected import");
    }

    private static byte[] zipWithTwoEntries() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            for (String name : new String[] { "first.json", "second.json" }) {
                zip.putNextEntry(new ZipEntry(name));
                zip.write("{}".getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static class CloseTrackingInputStream extends FilterInputStream {

        boolean closed;

        CloseTrackingInputStream(InputStream in) {
            super(in);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }
}