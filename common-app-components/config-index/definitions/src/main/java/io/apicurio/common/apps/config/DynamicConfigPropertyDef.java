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

package io.apicurio.common.apps.config;

import java.util.Objects;

/**
 * @author eric.wittmann@gmail.com
 */
@SuppressWarnings("rawtypes")
public class DynamicConfigPropertyDef {

    private String name;
    private Class type;
    private String defaultValue;
    private String label;
    private String description;
    private String[] requires;

    /**
     * Constructor.
     */
    public DynamicConfigPropertyDef() {
    }

    /**
     * Constructor.
     * 
     * @param name the config property name
     * @param type the property type
     * @param defaultValue the default value of the config property
     */
    public DynamicConfigPropertyDef(String name, Class type, String defaultValue) {
        this.setName(name);
        this.setType(type);
        this.setDefaultValue(defaultValue);
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return the type
     */
    public Class getType() {
        return type;
    }

    /**
     * @param type the type to set
     */
    public void setType(Class type) {
        this.type = type;
    }

    /**
     * Returns true if the given value is valid for this dynamic property.
     * 
     * @param value the value to test
     * @return true if the value is valid
     */
    public boolean isValidValue(String value) {
        // TODO this is very rudimentary. We should try to leverage MP-Config if possible to validate values.
        try {
            if (this.getType() == Long.class) {
                Long.parseLong(value);
            }
            if (this.getType() == Integer.class) {
                Integer.parseInt(value);
            }
            if (this.getType() == Boolean.class) {
                if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                    throw new Exception("Invalid boolean value: " + value);
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return Objects.hash(getName());
    }

    /**
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        DynamicConfigPropertyDef other = (DynamicConfigPropertyDef) obj;
        return Objects.equals(getName(), other.getName());
    }

    /**
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "DynamicConfigPropertyDef [name=" + name + ", type=" + type + "]";
    }

    /**
     * @return the defaultValue
     */
    public String getDefaultValue() {
        return defaultValue;
    }

    /**
     * @param defaultValue the defaultValue to set
     */
    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    /**
     * @return the label
     */
    public String getLabel() {
        return label;
    }

    /**
     * @param label the label to set
     */
    public void setLabel(String label) {
        this.label = label;
    }

    /**
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * @param description the description to set
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * @return the requires
     */
    public String[] getRequires() {
        return requires;
    }

    /**
     * @param requires the requires to set
     */
    public void setRequires(String[] requires) {
        this.requires = requires;
    }

}
