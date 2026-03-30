package io.apicurio.deployment;

import java.util.Optional;
import java.util.Set;

import org.junit.platform.engine.TestTag;
import org.junit.platform.launcher.tagexpression.TagExpression;


/**
 * Constants used by the deployment infrastructure.
 */
public final class Constants {

    private Constants() {
    }

    /**
     * Registry image placeholder.
     */
    static final String REGISTRY_IMAGE = "registry-image";

    /**
     * Tag for auth tests profile.
     */
    static final String AUTH = "auth";

    /**
     * Tag for kafkasql tests profile.
     */
    static final String KAFKA_SQL = "kafkasqlit";

    /**
     * Tag for kafkasql snapshotting tests profile.
     */
    static final String KAFKA_SQL_SNAPSHOTTING =
            "kafkasql-snapshotting";

    /**
     * Tag for sql tests profile.
     */
    static final String SQL = "sqlit";

    /**
     * Tag for kubernetesops tests profile.
     */
    static final String KUBERNETES_OPS = "kubernetesops";

    /**
     * Tag for iceberg tests profile.
     */
    static final String ICEBERG = "iceberg";

    /**
     * Active test groups from the Maven groups property.
     */
    public static final String ACTIVE_TEST_GROUPS_EXPRESSION =
            Optional.ofNullable(
                    System.getProperty("groups")).orElse("");

    /**
     * Normalize a groups expression for JUnit 5 tag parsing.
     * Maven/failsafe uses commas as OR delimiters, but
     * JUnit 5 tag expressions use {@code |}.
     *
     * @param expression the raw groups expression
     * @return normalized expression with commas replaced by |
     */
    static String normalize(final String expression) {
        if (expression == null || expression.isBlank()) {
            return "";
        }
        return expression.replace(",", " | ");
    }

    /**
     * Check whether a given group tag is active according
     * to the provided JUnit 5 tag expression. A group is
     * considered active if a test tagged with only that group
     * would be selected by the expression.
     *
     * @param expression JUnit 5 tag expression string
     * @param group the group tag to check
     * @return true if the group is active in the expression
     */
    static boolean isGroupActive(
            final String expression, final String group) {
        String normalized = normalize(expression);
        if (normalized.isEmpty()) {
            return false;
        }
        try {
            return TagExpression.parseFrom(normalized)
                    .tagExpressionOrThrow(
                            IllegalArgumentException::new)
                    .evaluate(Set.of(TestTag.create(group)));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid groups tag expression: '"
                            + normalized + "'", e);
        }
    }

    /**
     * Check whether a given group tag is active according
     * to the active test groups expression.
     *
     * @param group the group tag to check
     * @return true if the group is active
     */
    public static boolean isGroupActive(final String group) {
        return isGroupActive(
                ACTIVE_TEST_GROUPS_EXPRESSION, group);
    }
}
