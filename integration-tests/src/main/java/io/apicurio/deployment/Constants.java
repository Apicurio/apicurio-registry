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

    // ── JUnit 5 test group tag constants ──

    public static final String SMOKE = "smoke";
    public static final String SERDES = "serdes";
    public static final String ACCEPTANCE = "acceptance";
    public static final String MIGRATION = "migration";
    public static final String AUTH = "auth";
    public static final String KAFKA_SQL_SNAPSHOTTING = "kafkasql-snapshotting";
    public static final String DEBEZIUM = "debezium";
    public static final String DEBEZIUM_MYSQL = "debezium-mysql";
    public static final String DEBEZIUM_SNAPSHOT = "debezium-snapshot";
    public static final String DEBEZIUM_MYSQL_SNAPSHOT = "debezium-mysql-snapshot";
    public static final String KUBERNETES_OPS = "kubernetesops";
    public static final String ICEBERG = "iceberg";
    public static final String SEARCH = "search";

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
