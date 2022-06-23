package io.apicurio.registry.systemtests.framework;

import org.junit.jupiter.api.DisplayNameGenerator;

import java.lang.reflect.Method;

public class TestNameGenerator extends DisplayNameGenerator.ReplaceUnderscores {
    @Override
    public String generateDisplayNameForClass(Class<?> testClass) {
        return super.generateDisplayNameForClass(testClass);
    }

    @Override
    public String generateDisplayNameForNestedClass(Class<?> nestedClass) {
        return super.generateDisplayNameForNestedClass(nestedClass) + "...";
    }

    @Override
    public String generateDisplayNameForMethod(Class<?> testClass, Method testMethod) {
        return testMethod.getName();
    }
}
