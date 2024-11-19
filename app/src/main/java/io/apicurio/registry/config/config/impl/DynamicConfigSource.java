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

package io.apicurio.registry.config.config.impl;

import io.apicurio.common.apps.config.DynamicConfigPropertyDto;
import io.apicurio.common.apps.config.DynamicConfigStorage;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;

/**
 * A microprofile-config configsource. This class uses the dynamic config storage to read/write configuration
 * properties to, for example, a database.
 * <p>
 * TODO cache properties. this would need to be multi-tenant aware? probably should be implemented in the
 * storage layer!
 *
 * @author eric.wittmann@gmail.com
 */
public class DynamicConfigSource implements ConfigSource {

    private static final String LOG_PREFIX = "Could not get dynamic configuration value for {} in thread {}. ";

    private static final Logger log = LoggerFactory.getLogger(DynamicConfigSource.class);

    private static Optional<DynamicConfigStorage> storage = Optional.empty();

    public static void setStorage(DynamicConfigStorage configStorage) {
        storage = Optional.of(configStorage);
    }

    private static Optional<DynamicConfigPropertyIndexImpl> configIndex = Optional.empty();

    public static void setConfigurationIndex(DynamicConfigPropertyIndexImpl index) {
        configIndex = Optional.of(index);
    }

    @Override
    public int getOrdinal() {
        return 450; // Very high ordinal value:
                    // https://quarkus.io/guides/config-reference#configuration-sources
    }

    /**
     * @see ConfigSource#getPropertyNames()
     */
    @Override
    public Set<String> getPropertyNames() {
        return Collections.emptySet();
    }

    /**
     * @see ConfigSource#getValue(String)
     */
    @Override
    public String getValue(String propertyName) {
        String pname = normalizePropertyName(propertyName);
        if (configIndex.isPresent() && configIndex.get().hasProperty(pname)) {
            if (storage.isPresent()) {
                if (storage.get().isReady()) { // TODO Merge the ifs after removing logging
                    DynamicConfigPropertyDto dto = storage.get().getConfigProperty(pname);
                    if (dto != null) {
                        log.debug("Got dynamic configuration value {} for {} in thread {}", dto.getValue(),
                                pname, Thread.currentThread().getName());
                        return dto.getValue();
                    } else {
                        log.debug(LOG_PREFIX + "Storage returned null.", pname,
                                Thread.currentThread().getName());
                    }
                } else {
                    log.debug(LOG_PREFIX + "Storage is not ready.", pname, Thread.currentThread().getName());
                }
            } else {
                log.debug(LOG_PREFIX + "Storage is not present.", pname, Thread.currentThread().getName());
            }
        }
        return null;
    }

    private String normalizePropertyName(String propertyName) {
        if (propertyName == null || !propertyName.startsWith("%")) {
            return propertyName;
        }
        int idx = propertyName.indexOf(".");
        if (idx >= propertyName.length()) {
            return propertyName;
        }
        return propertyName.substring(idx + 1);
    }

    /**
     * @see ConfigSource#getName()
     */
    @Override
    public String getName() {
        return getClass().getSimpleName();
    }

}
