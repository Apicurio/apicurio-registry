///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS org.jboss:jandex:3.1.7


import io.apicurio.common.apps.config.ConfigPropertyCategory;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexReader;
import org.jboss.jandex.Main;
import org.jboss.jandex.Type;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

public class generateAllConfigPartial {

    private static Map<String, Option> allConfiguration = new HashMap();
    private static Set<String> skipProperties = Set.of("quarkus.oidc.auth-server-url");

    static class Option {
        final String name;
        final String category;
        final String description;
        final String type;
        final String defaultValue;
        String availableSince;

        public Option(String name, String category, String description, String type, String defaultValue, String availableSince) {
            this.name = name;
            this.category = category;
            this.description = description;
            this.type = type;
            this.defaultValue = defaultValue;
            this.availableSince = availableSince;
        }

        public String getName() {
            return name;
        }

        public String getCategory() {
            return category;
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

        public void setAvailableFrom(String af) {
            availableSince = af;
        }

        public String getAvailableFrom() {
            return availableSince;
        }

        @Override
        public String toString() {
            return "Option{" +
                    "name='" + name + '\'' +
                    ", category='" + category + '\'' +
                    ", description='" + description + '\'' +
                    ", type='" + type + '\'' +
                    ", defaultValue='" + defaultValue + '\'' +
                    ", availableSince='" + availableSince + '\'' +
                    '}';
        }

        public String toMDLine() {
            String af = availableSince == null ? " " : " `" + availableSince + "` ";
            return "| `" + name + "` | `" + category + "` | `" + type + "` | `" + defaultValue + "` |" + af + "| " + description + " |";
        }

        public String toAdoc() {
            String df = defaultValue == null || defaultValue.trim().isEmpty() ? "" : "`" + defaultValue + "`";
            String af = availableSince == null || availableSince.trim().isEmpty() ? "" : "`" + availableSince + "`";
            return "|`" + name + "`\n" +
                    "|`" + type + "`\n" +
                    "|" + df + "\n" +
                    "|" + af + "\n" +
                    "|" + description + "\n";
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

    public static Map<String, Option> extractConfigurations(String jarFile, Map<String, Option> allConfiguration) throws Exception {
        Main.main(new String[]{jarFile});

        // Jandex dance
        FileInputStream input = null;
        try {
            input = new FileInputStream(jarFile.replace(".jar", "-jar.idx"));
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

        DotName configProperty = DotName.createSimple("org.eclipse.microprofile.config.inject.ConfigProperty");
        List<AnnotationInstance> configAnnotations = index.getAnnotations(configProperty);

        for (AnnotationInstance annotation : configAnnotations) {
            if (annotation.value("name") == null) {
                continue;
            }

            var configName = annotation.value("name").value().toString();
            if (allConfiguration.containsKey(configName)) {
                continue;
            }
            if (skipProperties.contains(configName)) {
                continue;
            }
            switch (annotation.target().kind()) {
                case FIELD:
                    configName = configName.replace("app.authn.", "apicurio.auth.");


                    var defaultValue = Optional.ofNullable(annotation.value("defaultValue")).map(v -> v.value().toString()).orElse("");
                    var type = annotation.target().asField().type();

                    // Extract more infos if available:
                    var info = annotation
                            .target()
                            .asField()
                            .annotations()
                            .stream()
                            .filter(a -> a.name().toString().equals("io.apicurio.common.apps.config.Info"))
                            .findFirst();

                    if (!info.isPresent()) {
                        throw new IllegalArgumentException("The field: \"" + annotation.target() + "\" is annotated with @ConfigProperty but not with @io.apicurio.common.apps.config.Info");
                    }

                    var variant = (String) info.get().value("category").value();
                    var category = ConfigPropertyCategory.valueOf(variant).getRawValue();
                    var description = Optional.ofNullable(info.get().value("description")).map(v -> v.value().toString()).orElse("");

                    var availableSince = Optional.ofNullable(info.get().value("registryAvailableSince"))
                            .map(v -> v.value().toString()).
                            orElse(Optional.ofNullable(info.get().value("availableSince"))
                                    .map(v -> v.value().toString()).
                                    orElse(""));

                    allConfiguration.put(configName, new Option(
                            configName,
                            category,
                            description,
                            resolveTypeName(type),
                            defaultValue,
                            availableSince
                    ));
                    break;
            }
        }

        return allConfiguration;
    }

    public static void main(String... args) throws Exception {

        if (args.length < 3) {
            System.err.println("This script needs 3 arguments: version, baseDir, commonComponentsVersion");
            System.exit(-1);
        }

        String currentVersion = args[0];
        String baseDir = args[1];
        String commonComponentsVersion = args[2];
        String templateFile = baseDir + "/modules/ROOT/partials/getting-started/ref-registry-all-configs.template";
        String destinationFile = baseDir + "/modules/ROOT/partials/getting-started/ref-registry-all-configs.adoc";

        // TODO: include all the relevant jars, to be determined
        // Extract configuration from Jandex
        extractConfigurations(baseDir + "/../app/target/apicurio-registry-app-" + currentVersion + ".jar", allConfiguration);

        // TODO
        // How to handle dynamic RegistryProperties -> we can scan but the configuration is dynamic after it ...

        // STEP 2: BEST EFFORT - parse configurations from reference properties files
        Properties props = new Properties();
        try {
            //load a properties file from class path, inside static method
            props.load(new FileInputStream(baseDir + "/../app/src/main/resources/application.properties"));
        } catch (Exception e) {
            e.printStackTrace();
        }
        for (var prop : props.entrySet()) {

            var key = prop.getKey().toString();
            var value = prop.getValue().toString();
            if (allConfiguration.containsKey(key)) {
                // Injecting the default value/substitution
                var opt = allConfiguration.get(key);
                allConfiguration.put(key,
                        new Option(
                                key,
                                opt.getCategory(),
                                opt.getDescription(),
                                opt.getType(),
                                opt.getDefaultValue(),
                                opt.getAvailableFrom()
                        ));
            } else {
                if (key.startsWith("apicurio.")) {
                    allConfiguration.put(key,
                            new Option(
                                    key,
                                    "unknown",
                                    "",
                                    "unknown",
                                    value,
                                    ""
                            ));
                } else {
                    // Skip Quarkus property and unknowns!
                }
            }
        }

        // Read the template file
        var template = new String(Files.readAllBytes(Paths.get(templateFile)), StandardCharsets.UTF_8);

        try (var dest = new FileWriter(destinationFile)) {

            dest.write(template);

            var categories = new HashSet<String>();
            categories.addAll(allConfiguration.values().stream().map(c -> c.getCategory()).collect(Collectors.toList()));

            categories.remove("hidden");

            for (var category : categories.stream().sorted().collect(Collectors.toList())) {

                dest.write("== " + category + "\n");

                dest.write("." + category + " configuration options\n");
                dest.write("[.table-expandable,width=\"100%\",cols=\"6,3,2,3,5\",options=\"header\"]\n");
                dest.write("|===\n");
                dest.write("|Name\n");
                dest.write("|Type\n");
                dest.write("|Default\n");
                dest.write("|Available from\n");
                dest.write("|Description\n");

                for (var config : allConfiguration
                        .values()
                        .stream()
                        .filter(c -> c.getCategory().equals(category))
                        .sorted((o1, o2) -> o1.getName().compareTo(o2.getName())).collect(Collectors.toList())) {
                    dest.write(config.toAdoc());
                }

                dest.write("|===\n\n");
            }
        }
    }
}
