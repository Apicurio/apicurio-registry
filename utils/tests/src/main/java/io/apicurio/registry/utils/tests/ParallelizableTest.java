package io.apicurio.registry.utils.tests;

import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Execution(ExecutionMode.CONCURRENT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
// ^ makes the default "safe behavior" explicit
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE) // class-level only
public @interface ParallelizableTest {
}
