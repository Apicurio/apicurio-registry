/*
 * Copyright 2023 Red Hat
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

package io.apicurio.tests.dbupgrade.sql;

import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.utils.tests.MultitenancyNoAuthTestProfile;
import io.apicurio.registry.utils.tests.PostgreSqlEmbeddedTestResource;
import io.apicurio.registry.utils.tests.SimpleDisplayName;
import io.apicurio.tests.ApicurioRegistryBaseIT;
import io.apicurio.tests.dbupgrade.UpgradeTestsDataInitializer;
import io.apicurio.tests.utils.Constants;
import io.apicurio.tests.utils.TestSeparator;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import java.util.List;

/**
 * @author Carles Arnal
 */
@DisplayNameGeneration(SimpleDisplayName.class)
@TestInstance(Lifecycle.PER_CLASS)
@Tag(Constants.DB_UPGRADE)
@Tag(Constants.SQL)
@TestProfile(MultitenancyNoAuthTestProfile.class)
@QuarkusIntegrationTest
@QuarkusTestResource(value = PostgreSqlEmbeddedTestResource.class, restrictToAnnotatedClass = true)
@QuarkusTestResource(value = SqlUpgradeTestInitializer.class, restrictToAnnotatedClass = true)
public class SqlStorageUpgradeIT extends ApicurioRegistryBaseIT implements TestSeparator, Constants {

    public static List<UpgradeTestsDataInitializer.TenantData> data;

    public static RegistryClient upgradeTenantClient;

    @Override
    public void cleanArtifacts() throws Exception {
        //Don't clean up
    }

    @Test
    public void testStorageUpgrade() throws Exception {
        //First verify the data created in the old registry version.
        UpgradeTestsDataInitializer.verifyData(data);
        //Add more data to the new registry instance.
        UpgradeTestsDataInitializer.createMoreArtifacts(data);
        //Verify the new data.
        UpgradeTestsDataInitializer.verifyData(data);
    }
}
