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

package io.apicurio.registry.storage.dto;

/**
 * @author eric.wittmann@gmail.com
 */
public class SearchFilter {

    private SearchFilterType type;
    private Object value;

    /**
     * Constructor.
     */
    public SearchFilter() {
    }

    /**
     * Constructor.
     * @param type
     * @param value string
     */
    public SearchFilter(SearchFilterType type, String value) {
        this.type = type;
        this.value = value;
    }

    /**
     * Constructor.
     * @param type
     * @param value integer
     */
    public SearchFilter(SearchFilterType type, Integer value) {
        this.type = type;
        this.value = value;
    }

    /**
     * @return the string value
     */
    public String getStringValue() {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return (String) value;
        }
        throw new IllegalStateException("value is not of type string");
    }

    /**
     * @return the integer value
     */
    public Integer getIntegerValue() {
        if (value == null) {
            return null;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }
        throw new IllegalStateException("value is not of type integer");
    }


    /**
     * @param value the value to set
     */
    public void setStringValue(String value) {
        this.value = value;
    }

    /**
     * @return the type
     */
    public SearchFilterType getType() {
        return type;
    }

    /**
     * @param type the type to set
     */
    public void setType(SearchFilterType type) {
        this.type = type;
    }

    /**
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "SearchFilter [type=" + type + ", value=" + value + "]";
    }

}
