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

import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.slf4j.Logger;

/**
 * @author eric.wittmann@gmail.com
 */
public class HandleFactoryProducer {

    @Inject
    @Named("application")
    AgroalDataSource dataSource;

    @Inject
    Logger logger;

    @Produces
    @ApplicationScoped
    public HandleFactory produceHandleFactory() {
        return new DefaultHandleFactory(dataSource, logger);
    }
}
