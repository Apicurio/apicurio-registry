///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS org.jboss:jandex:2.4.3.Final

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexReader;
import org.jboss.jandex.Main;
import org.jboss.jandex.Type;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;

import static java.lang.System.*;

public class genConfigDocs {

    private static Map<String, Option> allConfiguration = new HashMap<>();

    static class Option {
        final String name;
        final String description;
        final String type;
        final String defaultValue;

        public Option(String name, String description, String type, String defaultValue) {
            this.name = name;
            this.description = description;
            this.type = type;
            this.defaultValue = defaultValue;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public String getType() {
            return type;
        }

        public String getDefaultValue() {
            return defaultValue;
        }

        @Override
        public String toString() {
            return "Option{" +
                    "name='" + name + '\'' +
                    ", description='" + description + '\'' +
                    ", type='" + type + '\'' +
                    ", defaultValue='" + defaultValue + '\'' +
                    '}';
        }

        public String toMDLine() {
            return "| `" + name + "` | `" + type + "` | `" + defaultValue + "` | " + description + " |";
        }
    }

    public static String resolveTypeName(Type t) {
        var prefix = t.name().withoutPackagePrefix().toLowerCase(Locale.ROOT);

        try {
            t.asParameterizedType();
            if (
                    t.name().packagePrefix().equals("javax.enterprise.inject") &&
                            t.name().withoutPackagePrefix().equals("Instance")) {
                return resolveTypeName(t.asParameterizedType().arguments().get(0));
            } else if (
                    t.name().packagePrefix().equals("java.util.function") &&
                            t.name().withoutPackagePrefix().equals("Supplier")) {
                return resolveTypeName(t.asParameterizedType().arguments().get(0)) + " [dynamic]";
            } else {
                return prefix + t.asParameterizedType()
                        .arguments()
                        .stream()
                        .map(p -> resolveTypeName(p))
                        .collect(Collectors.joining(", ", "<", ">"));
            }
        } catch (IllegalArgumentException e) {
            // Not a parametrized type
            return prefix;
        }
    }

    public static void main(String... args) {
        
        // TODO: include all the relevant jars, to be determined
        // Run Jandex on required jar files
        Main.main(new String[]{"app/target/apicurio-registry-app-2.2.6-SNAPSHOT.jar"});

        // Jandex dance
        FileInputStream input = null;
        try {
            input = new FileInputStream("app/target/apicurio-registry-app-2.2.6-SNAPSHOT-jar.idx");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        IndexReader reader = new IndexReader(input);
        Index index = null;
        try {
            index = reader.read();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                input.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // STEP 1: Populate all the ConfigProperty configurations
        DotName configProperty = DotName.createSimple("org.eclipse.microprofile.config.inject.ConfigProperty");
        List<AnnotationInstance> configAnnotations = index.getAnnotations(configProperty);

        for (AnnotationInstance annotation : configAnnotations) {
            switch (annotation.target().kind()) {
                case FIELD:
                    var configName = annotation.value("name").value().toString();
                    var defaultValue = Optional.ofNullable(annotation.value("defaultValue")).map(v -> v.value().toString()).orElse("");
                    var type = annotation.target().asField().type();

                    // Extract description if available:
                    var extractedDescription = annotation
                            .target()
                            .asField()
                            .annotations()
                            .stream()
                            .filter(a -> a.name().toString().equals("io.apicurio.common.apps.config.Dynamic"))
                            .findFirst()
                            .map(d -> Optional.ofNullable(d.value("description")).map(v -> v.value().toString()).orElse(""));

                    var description = extractedDescription.isPresent() ? extractedDescription.get() : "";

                    allConfiguration.put(configName, new Option(
                            configName,
                            description,
                            resolveTypeName(type),
                            defaultValue
                    ));
                    break;
            }
        }

        // TODO
        // How to handle dynamic RegistryProperties -> we can scan but the configuration is dynamic after it ...


        // STEP 2: parse configurations from reference properties files
        Properties props = new Properties();
        try {
            //load a properties file from class path, inside static method
            props.load(new FileInputStream("app/src/main/resources/application.properties"));
        } catch (Exception e) {
            e.printStackTrace();
        }
        for (var prop: props.entrySet()) {

            var key = prop.getKey().toString();
            var value = prop.getValue().toString();
            if (allConfiguration.containsKey(key)) {
                // System.out.println(prop.getValue());
                // Injecting the default value/substitution
                var opt = allConfiguration.get(key);
                allConfiguration.put(key,
                        new Option(
                                key,
                                opt.getDescription(),
                                opt.getType(),
                                value
                        ));
            } else {
                if (key.startsWith("registry.")) {
                    allConfiguration.put(key,
                            new Option(
                                    key,
                                    "",
                                    "unknown",
                                    value
                            ));
                } else {
                    // Skip Quarkus property and unknowns!
                    // System.out.println("Skipping " + key);
                }
            }
        }

        System.out.println("| name | type | default | description |");
        System.out.println("| --- | --- | --- | --- |");
        for (var config: allConfiguration.values().stream().sorted((o1, o2) -> o1.getName().compareTo(o2.getName())).collect(Collectors.toList())) {
            System.out.println(config.toMDLine());
        }
    }
}
