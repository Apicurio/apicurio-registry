package io.apicurio.registry.metrics.usage;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks the test field that {@link OtlpReceiverResource} fills with the receiver it started. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@interface InjectOtlpReceiver {
}
