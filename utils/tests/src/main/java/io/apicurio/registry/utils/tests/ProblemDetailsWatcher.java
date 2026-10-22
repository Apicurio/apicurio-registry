package io.apicurio.registry.utils.tests;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JUnit test watcher that prints detailed information about ProblemDetails and RuleViolationProblemDetails
 * exceptions using reflection to handle JUnit classloading issues.
 */
public class ProblemDetailsWatcher implements TestWatcher {

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailsWatcher.class);

    private static final String PROBLEM_DETAILS_CLASS = "io.apicurio.registry.rest.client.models.ProblemDetails";
    private static final String RULE_VIOLATION_PROBLEM_DETAILS_CLASS = "io.apicurio.registry.rest.client.models.RuleViolationProblemDetails";

    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        printProblemDetailsIfApplicable(cause);
    }

    /**
     * Checks if the exception is a ProblemDetails or RuleViolationProblemDetails exception
     * and prints detailed information using reflection.
     *
     * @param throwable the exception thrown by the test
     */
    private void printProblemDetailsIfApplicable(Throwable throwable) {
        if (throwable == null) {
            return;
        }

        String className = throwable.getClass().getName();
        boolean isProblemDetails = PROBLEM_DETAILS_CLASS.equals(className);
        boolean isRuleViolationProblemDetails = RULE_VIOLATION_PROBLEM_DETAILS_CLASS.equals(className);

        if (isProblemDetails || isRuleViolationProblemDetails) {
            log.error("\n========================================");
            log.error("Exception Details: {}", className);
            log.error("========================================");

            printField(throwable, "detail");
            printField(throwable, "instance");
            printField(throwable, "name");
            printField(throwable, "status");
            printField(throwable, "title");
            printField(throwable, "type");

            if (isRuleViolationProblemDetails) {
                printCauses(throwable);
            }

            log.error("========================================\n");
        }
    }

    /**
     * Prints the value of a field using reflection.
     *
     * @param object the object containing the field
     * @param fieldName the name of the field to print
     */
    private void printField(Object object, String fieldName) {
        try {
            String getterName = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
            java.lang.reflect.Method getter = object.getClass().getMethod(getterName);
            Object value = getter.invoke(object);
            log.error("{}: {}", fieldName, value);
        } catch (Exception e) {
            log.error("{}: <unable to retrieve: {}>", fieldName, e.getMessage());
        }
    }

    /**
     * Prints the causes field for RuleViolationProblemDetails using reflection.
     *
     * @param object the RuleViolationProblemDetails object
     */
    private void printCauses(Object object) {
        try {
            java.lang.reflect.Method getCauses = object.getClass().getMethod("getCauses");
            Object causes = getCauses.invoke(object);

            if (causes == null) {
                log.error("causes: null");
                return;
            }

            if (causes instanceof java.util.List) {
                @SuppressWarnings("unchecked")
                java.util.List<Object> causeList = (java.util.List<Object>) causes;

                if (causeList.isEmpty()) {
                    log.error("causes: []");
                } else {
                    log.error("causes:");
                    for (int i = 0; i < causeList.size(); i++) {
                        Object cause = causeList.get(i);
                        log.error("  [{}]:", i);
                        printCauseDetails(cause);
                    }
                }
            } else {
                log.error("causes: <unexpected type: {}>", causes.getClass().getName());
            }
        } catch (Exception e) {
            log.error("causes: <unable to retrieve: {}>", e.getMessage());
        }
    }

    /**
     * Prints the details of a single RuleViolationCause using reflection.
     *
     * @param cause the RuleViolationCause object
     */
    private void printCauseDetails(Object cause) {
        if (cause == null) {
            log.error("    <null>");
            return;
        }

        try {
            java.lang.reflect.Method getContext = cause.getClass().getMethod("getContext");
            Object context = getContext.invoke(cause);
            log.error("    context: {}", context);
        } catch (Exception e) {
            log.error("    context: <unable to retrieve: {}>", e.getMessage());
        }

        try {
            java.lang.reflect.Method getDescription = cause.getClass().getMethod("getDescription");
            Object description = getDescription.invoke(cause);
            log.error("    description: {}", description);
        } catch (Exception e) {
            log.error("    description: <unable to retrieve: {}>", e.getMessage());
        }
    }
}
