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

package apicurio.common.app.components.config.index.it;

import io.apicurio.common.apps.config.Dynamic;
import io.apicurio.common.apps.config.DynamicConfigPropertyDto;
import io.apicurio.common.apps.config.DynamicConfigPropertyIndex;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Path("/config")
@ApplicationScoped
public class ConfigIndexResource {
    // add some rest methods here

    @Inject
    DynamicConfigPropertyIndex dynamicPropertyIndex;
    @Inject
    InMemoryDynamicConfigStorage storage;
    @Inject
    Config config;

    @Dynamic
    @ConfigProperty(name = "apicurio.properties.dynamic.string", defaultValue = "_DEFAULT_")
    Supplier<String> dynamicString;

    @Dynamic
    @ConfigProperty(name = "apicurio.properties.dynamic.int", defaultValue = "0")
    Supplier<Integer> dynamicInt;

    @Dynamic
    @ConfigProperty(name = "apicurio.properties.dynamic.long", defaultValue = "17")
    Supplier<Long> dynamicLong;

    @Dynamic
    @ConfigProperty(name = "apicurio.properties.dynamic.bool", defaultValue = "false")
    Supplier<Boolean> dynamicBool;

    @Dynamic(requires = "apicurio.properties.dynamic.bool=true")
    @ConfigProperty(name = "apicurio.properties.dynamic.bool.dep", defaultValue = "false")
    Supplier<Boolean> dynamicBoolDep;

    @ConfigProperty(name = "apicurio.properties.static.string", defaultValue = "default-value")
    String staticString;

    @GET
    @Path("/all")
    @Produces(MediaType.APPLICATION_JSON)
    public ConfigProps getAllProperties() {
        return new ConfigProps(dynamicPropertyIndex.getPropertyNames().stream()
                .map(pname -> new ConfigProp(pname, getPropertyValue(pname))).collect(Collectors.toList()));
    }

    @GET
    @Path("/accepted")
    @Produces(MediaType.APPLICATION_JSON)
    public ConfigProps getAcceptedProperties() {
        return new ConfigProps(dynamicPropertyIndex.getAcceptedPropertyNames().stream()
                .map(pname -> new ConfigProp(pname, getPropertyValue(pname))).collect(Collectors.toList()));
    }

    @Path("/all/{propertyName}")
    @GET
    @Produces("application/json")
    public ConfigProp getConfigProperty(@PathParam("propertyName") String propertyName) {
        String name = propertyName;
        String value = null;
        if ("apicurio.properties.dynamic.string".equals(propertyName)) {
            value = dynamicString.get();
        }
        if ("apicurio.properties.dynamic.int".equals(propertyName)) {
            value = dynamicInt.get().toString();
        }
        if ("apicurio.properties.dynamic.long".equals(propertyName)) {
            value = dynamicLong.get().toString();
        }
        if ("apicurio.properties.dynamic.bool".equals(propertyName)) {
            value = dynamicBool.get().toString();
        }
        if ("apicurio.properties.dynamic.bool.dep".equals(propertyName)) {
            value = dynamicBoolDep.get().toString();
        }
        return new ConfigProp(name, value);
    }

    @Path("/update")
    @GET
    public void updateBooleanProperty() {
        DynamicConfigPropertyDto dto = new DynamicConfigPropertyDto("apicurio.properties.dynamic.bool",
                "true");
        this.storage.setConfigProperty(dto);
    }

    private String getPropertyValue(String propertyName) {
        Optional<String> optionalValue = config.getOptionalValue(propertyName, String.class);
        return optionalValue
                .orElseGet(() -> this.dynamicPropertyIndex.getProperty(propertyName).getDefaultValue());
    }

}
