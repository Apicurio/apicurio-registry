/*
 * Copyright 2022 Red Hat
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

package io.apicurio.tests.dbupgrade;

import io.apicurio.registry.utils.tests.SimpleDisplayName;
import io.apicurio.tests.common.Constants;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;

@DisplayNameGeneration(SimpleDisplayName.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag(Constants.DB_UPGRADE)
@Tag(Constants.SQL_SERVER)
@Disabled //Disabled for now since we're introducing the SQLServer image, enabled it when there are different versions of the image.
public class SqlServerStorageUpgradeIT extends SqlStorageUpgradeIT {
}
