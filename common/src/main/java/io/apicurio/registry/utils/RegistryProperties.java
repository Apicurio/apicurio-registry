package io.apicurio.registry.utils;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Retention(RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
public @interface RegistryProperties {

    /**
     * The prefixes of keys of configuration properties that should be included.
     * Keys with a later prefix in the array override the earlier ones.
     */
    String[] prefixes();

    /**
     * If a configuration property (excluding the prefix) key is found, but has no value,
     * the default value from this array is used.
     * This can be used to set empty string values for properties that require it.
     * Each entry must be of the form "key-prefix=value".
     */
    String[] defaults() default {};

    /**
     * Keys of configuration properties (excluding the prefix) that should be ignored.
     * This is useful, for example, if we have defined standard configuration property
     * in addition to a prefix-based set, and we don't want it to be included in the resulting Properties object.
     */
    String[] excluded() default {};
}
