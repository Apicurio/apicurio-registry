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

import java.util.List;

/**
 * @author eric.wittmann@gmail.com
 */
public class ConfigProps {

    private List<ConfigProp> properties;

    public ConfigProps() {
    }

    public ConfigProps(List<ConfigProp> properties) {
        this.properties = properties;
    }

    public List<ConfigProp> getProperties() {
        return properties;
    }

    public void setProperties(List<ConfigProp> properties) {
        this.properties = properties;
    }

    public ConfigProp getProperty(String propertyName) {
        for (ConfigProp configProp : properties) {
            if (configProp.getName().equals(propertyName)) {
                return configProp;
            }
        }
        return null;
    }

    public String getPropertyValue(String propertyName) {
        return getProperty(propertyName).getValue();
    }

    public boolean hasProperty(String propertyName) {
        return getProperty(propertyName) != null;
    }

}
