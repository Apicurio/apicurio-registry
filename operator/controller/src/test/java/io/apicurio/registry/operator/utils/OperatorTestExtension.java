package io.apicurio.registry.operator.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * JUnit 5 extension that provides two capabilities for operator integration tests:
 *
 * <p>
 * <b>Retry on failure</b> ({@link InvocationInterceptor}): Test methods annotated with {@link RetryTest} are
 * automatically retried on failure. The {@code @AfterEach} cleanup is called between attempts to reset state.
 * Each retry is logged with the attempt number and the triggering exception.
 *
 * <p>
 * <b>Cluster diagnostics on failure</b> ({@link AfterTestExecutionCallback}): When a test fails (after all
 * retries are exhausted, or immediately for non-retried tests), the extension dumps the Kubernetes cluster
 * state to the log. Uses {@code AfterTestExecutionCallback} so diagnostics run <b>before</b>
 * {@code @AfterEach} cleanup, while CRs and Pods are still present. The test class must implement
 * {@link OperatorTestContext} to provide the client and namespace.
 *
 * <p>
 * Register on test base classes with {@code @ExtendWith(OperatorTestExtension.class)} — all subclasses
 * inherit it automatically.
 */
public class OperatorTestExtension implements InvocationInterceptor, AfterTestExecutionCallback {

    private static final Logger log = LoggerFactory.getLogger(OperatorTestExtension.class);

    @Override
    public void interceptTestMethod(Invocation<Void> invocation,
            ReflectiveInvocationContext<Method> invocationContext,
            ExtensionContext extensionContext) throws Throwable {

        Method method = invocationContext.getExecutable();
        RetryTest retryTest = method.getAnnotation(RetryTest.class);

        if (retryTest == null) {
            invocation.proceed();
            return;
        }

        int totalAttempts = retryTest.maxRetries() + 1;
        Throwable lastException = null;

        for (int attempt = 1; attempt <= totalAttempts; attempt++) {
            try {
                if (attempt == 1) {
                    invocation.proceed();
                } else {
                    method.setAccessible(true);
                    method.invoke(invocationContext.getTarget().orElseThrow());
                }
                if (attempt > 1) {
                    log.info("========================================");
                    log.info("TEST PASSED on attempt {}/{}: {}", attempt, totalAttempts, method.getName());
                    log.info("========================================");
                }
                return;
            } catch (InvocationTargetException e) {
                lastException = e.getCause();
            } catch (Throwable e) {
                lastException = e;
            }

            if (attempt < totalAttempts) {
                log.warn("========================================");
                log.warn("RETRY {}/{} for test: {}", attempt + 1, totalAttempts, method.getName());
                log.warn("Failed with: {} - {}", lastException.getClass().getSimpleName(),
                        lastException.getMessage());
                log.warn("========================================");
                runCleanupBetweenRetries(invocationContext);
            }
        }

        log.error("========================================");
        log.error("TEST FAILED after {} attempts: {}", totalAttempts, method.getName());
        log.error("========================================");
        throw lastException;
    }

    @Override
    public void afterTestExecution(ExtensionContext context) {
        if (context.getExecutionException().isEmpty()) {
            return;
        }
        Object testInstance = context.getRequiredTestInstance();
        if (testInstance instanceof OperatorTestContext ctx) {
            var client = ctx.getClient();
            var namespace = ctx.getNamespace();
            if (client != null && namespace != null) {
                ClusterDiagnostics.dump(client, namespace, ctx.isOLMTest());
            } else {
                log.warn("Cannot dump cluster diagnostics: client={}, namespace={}", client, namespace);
            }
        } else {
            log.warn("Test class does not implement OperatorTestContext, skipping cluster diagnostics");
        }
    }

    private void runCleanupBetweenRetries(ReflectiveInvocationContext<Method> invocationContext) {
        invocationContext.getTarget().ifPresent(testInstance -> {
            Method afterEach = findAfterEachMethod(testInstance.getClass());
            if (afterEach == null) {
                return;
            }
            try {
                log.info("Running @AfterEach cleanup between retry attempts");
                afterEach.setAccessible(true);
                afterEach.invoke(testInstance);
            } catch (Exception e) {
                log.warn("Cleanup between retries failed", e);
            }
        });
    }

    private Method findAfterEachMethod(Class<?> clazz) {
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.isAnnotationPresent(AfterEach.class)) {
                    return m;
                }
            }
        }
        return null;
    }
}
