/*
 * Copyright 2021 Red Hat
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

import io.apicurio.registry.rest.client.AdminClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.v2.beans.ArtifactMetaData;
import io.apicurio.registry.rest.v2.beans.RoleMapping;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RoleType;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * @author eric.wittmann@gmail.com
 */
@QuarkusTest
// Disabled for now since this profile is initializing a h2 database even when building the sql profile which uses an embedded postgres
@TestProfile(SqlStorageUpgradeTestProfile.class)
@Disabled
public class SqlStorageUpgradeTest extends AbstractResourceTestBase {

    @Test
    public void testUpgradeFromV1toV2() throws Exception {
        RegistryClient client = createRestClientV2();
        AdminClient adminClient = createAdminClientV2();
        ArtifactMetaData metaData = client.getArtifactMetaData("TestGroup", "TestArtifact");
        // Expected values can be found in "SqlStorageUpgradeTest.dml" in src/test/resources
        Assertions.assertEquals(101, metaData.getContentId());
        Assertions.assertEquals(5001, metaData.getGlobalId());
        Assertions.assertEquals(ArtifactType.JSON, metaData.getType());

        RoleMapping mapping = new RoleMapping();
        mapping.setPrincipalId("test_user");
        mapping.setRole(RoleType.ADMIN);
        adminClient.createRoleMapping(mapping);

        mapping = adminClient.getRoleMapping("test_user");
        Assertions.assertEquals(RoleType.ADMIN, mapping.getRole());
    }

}
