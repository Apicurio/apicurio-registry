package io.apicurio.registry;

import io.apicurio.registry.rest.ConflictException;
import io.apicurio.registry.rest.v3.impl.AdminResourceImpl;
import io.apicurio.registry.storage.StorageEvent;
import io.apicurio.registry.storage.StorageEventType;
import io.apicurio.registry.storage.importing.ImportExportConfigProperties;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class ImportLifecycleBeanUnitTest {

    @Test
    public void testConflictExceptionSetsStatusToSkipped() throws Exception {
        ImportLifecycleBean bean = new ImportLifecycleBean();
        bean.log = LoggerFactory.getLogger(ImportLifecycleBean.class);

        // Mock ImportExportConfigProperties with a local temp file URL
        bean.importExportProps = Mockito.mock(ImportExportConfigProperties.class);
        java.io.File tempFile = java.io.File.createTempFile("dummy-import", ".zip");
        tempFile.deleteOnExit();
        bean.importExportProps.registryImportUrlProp = Optional.of(tempFile.toURI().toURL());

        // Mock AdminResourceImpl to throw ConflictException when importData is called
        bean.v3Admin = Mockito.mock(AdminResourceImpl.class);
        Mockito.doThrow(new ConflictException("Registry not empty"))
               .when(bean.v3Admin).importData(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());

        // Trigger the onStorageReady event
        StorageEvent event = StorageEvent.builder().type(StorageEventType.READY).build();
        bean.onStorageReady(event);

        // Verify bean is ready
        Assertions.assertTrue(bean.isReady(), "ImportLifecycleBean should be ready when import is skipped due to existing data");
    }
}
