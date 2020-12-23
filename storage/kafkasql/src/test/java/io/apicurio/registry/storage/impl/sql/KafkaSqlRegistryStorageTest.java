/*
 * Copyright 2020 Red Hat
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

package io.apicurio.registry.storage.impl.sql;

import javax.inject.Inject;

import org.junit.Ignore;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.apicurio.registry.storage.AbstractRegistryStorageTest;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.impl.kafkasql.KafkaSqlRegistryStorage;
import io.apicurio.registry.storage.impl.kafkasql.sql.KafkaSqlStore;
import io.quarkus.test.junit.QuarkusTest;

/**
 * @author eric.wittmann@gmail.com
 */
@QuarkusTest
public class KafkaSqlRegistryStorageTest extends AbstractRegistryStorageTest {
    
    @Inject
    KafkaSqlRegistryStorage storage;
    @Inject
    KafkaSqlStore store;
    
    /**
     * @see io.apicurio.registry.storage.AbstractRegistryStorageTest#storage()
     */
    @Override
    protected RegistryStorage storage() {
        return storage;
    }

    /**
     * @see io.apicurio.registry.storage.AbstractRegistryStorageTest#initTenants()
     */
    @Override
    protected void initTenants() {
        // Create DB schemas for tenant 1 and 2
        Assertions.assertNotNull(store);
        Assertions.assertNotNull(store.handleFactory());
        store.handleFactory().withHandleQuiet(handle -> {
            handle.createUpdate("CREATE SCHEMA IF NOT EXISTS " + tenantId1).execute();
            handle.createUpdate("CREATE SCHEMA IF NOT EXISTS " + tenantId2).execute();
            return null;
        });
        
        // Initialize tenant 1
        switchToTenant1();
        store.initializeDatabase();
        
        // Initialize tenant 2
        switchToTenant2();
        store.initializeDatabase();
    }

    @Test @Ignore
    public void testMultiTenant_CreateArtifact() throws Exception {
    }

    @Test @Ignore
    public void testMultiTenant_CreateSameArtifact() throws Exception {
    }

    @Test @Ignore
    public void testMultiTenant_Search() throws Exception {
    }

    @Test @Ignore
    public void testMultiTenant_GlobalRules() {
    }

    @Test @Ignore
    public void testMultiTenant_ArtifactNotFound() throws Exception {
    }

    @Test @Ignore
    public void testMultiTenant_CreateArtifactRule() throws Exception {
    }

    @Test @Ignore
    public void testMultiTenant_CreateArtifactVersionWithMetaData() throws Exception {
    }

    @Test @Ignore
    public void testMultiTenant_CreateDuplicateArtifact() throws Exception {
    }

    @Test @Ignore
    public void testMultiTenant_UpdateArtifactMetaData() throws Exception {
    }

    @Test @Ignore
    public void testMultiTenant_UpdateArtifactRule() throws Exception {
    }

    @Test @Ignore
    public void testMultiTenant_UpdateArtifactState() throws Exception {
    }

    @Test @Ignore
    public void testMultiTenant_UpdateArtifactVersionMetaData() throws Exception {
    }

    @Test @Ignore
    public void testMultiTenant_UpdateArtifactVersionState() throws Exception {
    }

    
}
