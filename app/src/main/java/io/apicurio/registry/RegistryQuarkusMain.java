package io.apicurio.registry;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.annotations.QuarkusMain;


@QuarkusMain(name = "RegistryQuarkusMain")
public class RegistryQuarkusMain {
    public static void main(String... args) {
        Quarkus.run(args);
    }
}
