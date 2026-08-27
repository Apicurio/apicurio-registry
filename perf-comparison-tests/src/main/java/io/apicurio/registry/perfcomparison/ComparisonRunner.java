package io.apicurio.registry.perfcomparison;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ComparisonRunner {

    public static void main(String[] args) throws IOException, InterruptedException {
        String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        String results = System.getProperty("gatling.resultsFolder", "/results/gatling");
        List<String> command = new ArrayList<>(List.of(javaBin,
                "--add-opens", "java.base/java.lang=ALL-UNNAMED",
                "--add-opens", "java.base/java.util=ALL-UNNAMED",
                "--add-opens", "java.base/java.util.concurrent=ALL-UNNAMED",
                "--add-opens", "java.base/java.nio=ALL-UNNAMED",
                "--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED",
                "--add-opens", "java.base/sun.security.ssl=ALL-UNNAMED",
                "--add-opens", "java.base/sun.security.util=ALL-UNNAMED",
                "--add-opens", "java.base/java.net=ALL-UNNAMED",
                "--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED"));
        forwardProperty(command, "javax.net.ssl.trustStore");
        forwardProperty(command, "javax.net.ssl.trustStorePassword");
        command.addAll(List.of(
                "-cp", System.getProperty("java.class.path"), "io.gatling.app.Gatling",
                "-s", "io.apicurio.registry.perfcomparison.simulations.ConfluentApiSimulation",
                "-rf", results, "-rd", env("PRODUCT_NAME", "schema-registry") + " neutral comparison"));
        ProcessBuilder process = new ProcessBuilder(command);
        process.inheritIO();
        System.exit(process.start().waitFor());
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static void forwardProperty(List<String> command, String name) {
        String value = System.getProperty(name);
        if (value != null && !value.isBlank()) {
            command.add("-D" + name + "=" + value);
        }
    }
}
