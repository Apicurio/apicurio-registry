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

import org.apache.commons.lang3.tuple.Pair;

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
     * @param value object
     */
    private SearchFilter(SearchFilterType type, Object value) {
        this.type = type;
        this.value = value;
    }

    public static SearchFilter ofProperty(String propertyKey, String propertyValue) {
        return new SearchFilter(SearchFilterType.properties, Pair.<String, String>of(propertyKey, propertyValue));
    }

    public static SearchFilter ofGlobalId(Long value) {
        return new SearchFilter(SearchFilterType.globalId, value);
    }

    public static SearchFilter ofContentId(Long value) {
        return new SearchFilter(SearchFilterType.contentId, value);
    }

    public static SearchFilter ofName(String value) {
        return new SearchFilter(SearchFilterType.name, value);
    }

    public static SearchFilter ofDescription(String value) {
        return new SearchFilter(SearchFilterType.description, value);
    }

    public static SearchFilter ofGroup(String value) {
        return new SearchFilter(SearchFilterType.group, value);
    }

    public static SearchFilter ofLabel(String value) {
        return new SearchFilter(SearchFilterType.labels, value);
    }

    public static SearchFilter ofCanonicalHash(String value) {
        return new SearchFilter(SearchFilterType.canonicalHash, value);
    }

    public static SearchFilter ofContentHash(String value) {
        return new SearchFilter(SearchFilterType.contentHash, value);
    }

    public static SearchFilter ofEverything(String value) {
        return new SearchFilter(SearchFilterType.everything, value);
    }

    @SuppressWarnings("unchecked")
    public Pair<String, String> getPropertyFilterValue() {
        if (value == null) {
            return null;
        }
        if (this.value instanceof Pair) {
            return (Pair<String, String>) this.value;
        }
        throw new IllegalStateException("value is not of type pair");
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
    public Number getNumberValue() {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return (Number) value;
        }
        throw new IllegalStateException("value is not of type number");
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
