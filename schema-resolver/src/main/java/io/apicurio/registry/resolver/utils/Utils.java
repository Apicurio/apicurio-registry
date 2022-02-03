/*
 * Copyright 2020 Red Hat
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

package io.apicurio.registry.resolver.utils;

import java.util.function.Consumer;

/**
 * @author Ales Justin
 */
public class Utils {

    @SuppressWarnings("unchecked")
    public static <T> Class<T> loadClass(String javaType) {
        try {
            return (Class<T>) Thread.currentThread().getContextClassLoader().loadClass(javaType);
        } catch (ClassNotFoundException ignored) {
        }
        try {
            return (Class<T>) Class.forName(javaType);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    //TODO make the instantiation mechanism configurable
    @SuppressWarnings("unchecked")
    public static <V> void instantiate(Class<V> type, Object value, Consumer<V> setter) {
        if (value != null) {
            if (type.isInstance(value)) {
                setter.accept(type.cast(value));
            } else if (value instanceof Class && type.isAssignableFrom((Class<?>) value)) {
                //noinspection unchecked
                setter.accept(instantiate((Class<V>) value));
            } else if (value instanceof String) {
                Class<V> clazz = loadClass((String) value);
                setter.accept(instantiate(clazz));
            } else {
                throw new IllegalArgumentException(String.format("Cannot handle configuration [%s]: %s", type.getName(), value));
            }
        }
    }

    //TODO make the instantiation mechanism configurable
    public static <V> V instantiate(Class<V> clazz) {
        try {
            return clazz.getConstructor().newInstance();
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

}
