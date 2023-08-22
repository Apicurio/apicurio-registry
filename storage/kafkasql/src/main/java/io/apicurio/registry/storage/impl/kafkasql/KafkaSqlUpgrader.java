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

package io.apicurio.registry.storage.impl.kafkasql;

import io.apicurio.registry.storage.impl.sql.HandleFactory;
import io.apicurio.registry.storage.impl.sql.upgrader.ReferencesCanonicalHashUpgrader;
import io.apicurio.registry.storage.impl.sql.upgrader.ReferencesContentHashUpgrader;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * @author Fabian Martinez
 */
@ApplicationScoped
public class KafkaSqlUpgrader {


    @Inject
    HandleFactory handles;

    @Inject
    KafkaSqlProtobufCanonicalizerUpgrader protobufCanonicalizerUpgrader;

    ReferencesContentHashUpgrader contentHashUpgrader;
    ReferencesCanonicalHashUpgrader canonicalHashUpgrader;

    public void upgrade() {

        contentHashUpgrader = new ReferencesContentHashUpgrader();
        canonicalHashUpgrader = new ReferencesCanonicalHashUpgrader();

        handles.withHandleNoException(handle -> {

            protobufCanonicalizerUpgrader.upgrade(handle);
            contentHashUpgrader.upgrade(handle);
            canonicalHashUpgrader.upgrade(handle);

            return null;
        });

    }
}
