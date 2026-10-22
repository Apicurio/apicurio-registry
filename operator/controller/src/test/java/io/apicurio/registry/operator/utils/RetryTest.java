package io.apicurio.registry.operator.utils;

import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a test method for automatic retry on failure. This is a drop-in replacement for {@link Test @Test}
 * — do not use both annotations on the same method.
 *
 * <p>
 * When a test annotated with {@code @RetryTest} fails, the {@link OperatorTestExtension} will re-invoke it
 * up to {@link #maxRetries()} additional times. Between retry attempts, the {@code @AfterEach} cleanup method
 * is called to reset test state (e.g. delete CRs). Each retry is logged clearly with the attempt number and
 * the exception that triggered it.
 *
 * <p>
 * If the test passes on any attempt, the overall test result is a pass. If all attempts are exhausted, the
 * last exception is thrown and {@link ClusterDiagnostics} dumps the cluster state for debugging.
 *
 * <p>
 * Example usage:
 *
 * <pre>
 * &#64;RetryTest // 3 total attempts (1 + 2 retries)
 * void smoke() { ... }
 *
 * &#64;RetryTest(maxRetries = 4) // 5 total attempts
 * void flakyTest() { ... }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Test
public @interface RetryTest {

    /**
     * Maximum number of retry attempts after the initial failure. The total number of attempts is
     * {@code maxRetries + 1}. Defaults to 2 (3 total attempts).
     */
    int maxRetries() default 2;
}
