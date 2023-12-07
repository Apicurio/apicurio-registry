package io.apicurio.registry.types;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import jakarta.inject.Qualifier;

/**
 * Marks currently "used" bean.
 *
 */
@Qualifier
@Retention(RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.TYPE, ElementType.PARAMETER})
public @interface Current {
}
