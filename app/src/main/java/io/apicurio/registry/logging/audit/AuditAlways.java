package io.apicurio.registry.logging.audit;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Opts a read operation (GET) into the audit trail, for reads that are audit-relevant on their own, such
 * as exporting all registry data. Successful reads are otherwise not audited.
 */
@Retention(RUNTIME)
@Target(METHOD)
public @interface AuditAlways {

    /**
     * Why every call to this read operation is audited.
     */
    String reason();
}
