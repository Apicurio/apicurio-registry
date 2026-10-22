package io.apicurio.registry.cli.utils;

@FunctionalInterface
public interface Output {

    void print(String value);
}
