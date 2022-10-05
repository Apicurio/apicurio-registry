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

package io.apicurio.registry.ccompat.rest.v6.impl;

import javax.inject.Inject;

import org.slf4j.Logger;

import io.apicurio.registry.ccompat.store.RegistryStorageFacade;

/**
 * @author Ales Justin
 * @author Jakub Senko 'jsenko@redhat.com'
 */
public abstract class AbstractResource {

    @Inject
    RegistryStorageFacade facade;

    @Inject
    Logger log;

}
