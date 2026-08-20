package io.apicurio.registry.rest.v3.impl.shared;

import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.utils.impexp.Entity;
import io.apicurio.registry.utils.impexp.ManifestEntity;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class DataExporterTest {

    @Test
    void exportDataShouldFailOnWriteError() throws Exception {
        DataExporter exporter = new DataExporter();

        Field logField = DataExporter.class.getDeclaredField("log");
        logField.setAccessible(true);
        logField.set(exporter, LoggerFactory.getLogger(DataExporter.class));

        Field storageField = DataExporter.class.getDeclaredField("storage");
        storageField.setAccessible(true);

        RegistryStorage storage = mock(RegistryStorage.class);
        doAnswer(invocation -> {
            Function<Entity, Void> handler = invocation.getArgument(1);
            handler.apply(new ManifestEntity());
            throw new RuntimeException("write failure");
        }).when(storage).exportData(any(), any());

        storageField.set(exporter, storage);

        Response response = exporter.exportData();
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus(),
                "The response is prepared with 200 before the stream is written");

        StreamingOutput stream = (StreamingOutput) response.getEntity();
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        assertThrows(IOException.class, () -> stream.write(os),
                "A write error must propagate so the client does not receive a completed 200 OK");
    }
}
