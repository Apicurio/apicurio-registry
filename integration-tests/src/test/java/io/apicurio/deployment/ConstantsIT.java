package io.apicurio.deployment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConstantsIT {

    @Test
    void singleGroup() {
        assertTrue(Constants.isGroupActive("auth", "auth"));
        assertFalse(Constants.isGroupActive("auth", "smoke"));
    }

    @Test
    void orExpression() {
        String expr = "smoke | serdes | acceptance";
        assertTrue(Constants.isGroupActive(expr, "smoke"));
        assertTrue(Constants.isGroupActive(expr, "serdes"));
        assertTrue(Constants.isGroupActive(expr, "acceptance"));
        assertFalse(Constants.isGroupActive(expr, "auth"));
    }

    @Test
    void andExpression() {
        // AND means both tags must be present on a test.
        // A test tagged only "smoke" does NOT match "smoke & auth".
        String expr = "smoke & auth";
        assertFalse(Constants.isGroupActive(expr, "smoke"));
        assertFalse(Constants.isGroupActive(expr, "auth"));
    }

    @Test
    void parenthesizedExpression() {
        String expr = "(smoke | auth)";
        assertTrue(Constants.isGroupActive(expr, "smoke"));
        assertTrue(Constants.isGroupActive(expr, "auth"));
        assertFalse(Constants.isGroupActive(expr, "serdes"));
    }

    @Test
    void negatedGroupIsNotActive() {
        assertFalse(Constants.isGroupActive("!slow", "slow"));
    }

    @Test
    void mixedNegationAndPositive() {
        String expr = "smoke & !slow";
        assertTrue(Constants.isGroupActive(expr, "smoke"));
        assertFalse(Constants.isGroupActive(expr, "slow"));
    }

    @Test
    void negatedParenthesizedGroup() {
        assertFalse(
                Constants.isGroupActive("!(auth)", "auth"));
    }

    @Test
    void commaDelimited() {
        String expr =
                "debezium,debezium-snapshot,debezium-mysql";
        assertTrue(
                Constants.isGroupActive(expr, "debezium"));
        assertTrue(Constants.isGroupActive(
                expr, "debezium-snapshot"));
        assertTrue(Constants.isGroupActive(
                expr, "debezium-mysql"));
        assertFalse(
                Constants.isGroupActive(expr, "auth"));
    }

    @Test
    void nullExpression() {
        assertFalse(Constants.isGroupActive(null, "smoke"));
    }

    @Test
    void blankExpression() {
        assertFalse(Constants.isGroupActive("", "smoke"));
        assertFalse(
                Constants.isGroupActive("   ", "smoke"));
    }
}
