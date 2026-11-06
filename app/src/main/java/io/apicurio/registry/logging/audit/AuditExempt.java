package io.apicurio.registry.logging.audit;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Marks a resource implementation method that uses a mutating HTTP verb (POST, PUT, PATCH or DELETE) but
 * changes nothing, such as a search or lookup that takes its query in the request body. A successful call
 * to such a method produces no audit event; a denied call (401 or 403) still does. The reason is
 * required and is pinned by {@code AuditCoverageTest}.
 */
@Retention(RUNTIME)
@Target(METHOD)
public @interface AuditExempt {

    /**
     * Why the operation does not need an audit event.
     */
    String reason();
}
