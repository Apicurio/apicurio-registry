///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS org.jboss:jandex:2.4.3.Final
//DEPS org.jsoup:jsoup:1.15.1

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexReader;
import org.jboss.jandex.Main;
import org.jboss.jandex.Type;

import java.io.*;
import java.util.*;
import java.net.*;
import java.nio.file.*;
import java.util.stream.Collectors;

import static java.lang.System.*;

public class genConfigDocs {

    private static Map<String, Option> allConfiguration = new HashMap();

    private static Map<String, Map<String, Option>> prevConfigurations = new HashMap();

    private static String currentVersion = "2.2.6-SNAPSHOT";

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
            String af = availableSince == null ?  " " :  " `" + availableSince + "` ";
            return "| `" + name +  "` | `" + category + "` | `" + type + "` | `" + defaultValue + "` |" + af + "| " + description + " |";
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

    public static Map<String, Option> extractConfigurations(String jarFile) {
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

        Map<String, Option> allConfiguration = new HashMap();

        DotName configProperty = DotName.createSimple("org.eclipse.microprofile.config.inject.ConfigProperty");
        List<AnnotationInstance> configAnnotations = index.getAnnotations(configProperty);

        for (AnnotationInstance annotation : configAnnotations) {
            switch (annotation.target().kind()) {
                case FIELD:
                    var configName = annotation.value("name").value().toString();
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

                    var category = "";
                    var description = "";
                    var availableSince = "";
                    if (info.isPresent()) {
                        category = Optional.ofNullable(info.get().value("category")).map(v -> v.value().toString()).orElse("");
                        description = Optional.ofNullable(info.get().value("description")).map(v -> v.value().toString()).orElse("");
                        availableSince = Optional.ofNullable(info.get().value("availableSince")).map(v -> v.value().toString()).orElse("");
                    }

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

    private static String findOldest(String optionName) {
        return prevConfigurations
                .keySet()
                .stream()
                .sorted()
                .filter(version -> prevConfigurations.get(version).containsKey(optionName))
                .findFirst()
                .orElse(currentVersion);
    }

    public static void main(String... args) throws Exception {
        // Download from repo1 maven old artifacts
        // String repo1 = "https://repo1.maven.org/maven2/io/apicurio/apicurio-registry-app/";
        // Document doc = Jsoup.connect(repo1).get();
        
        // for (Element file : doc.select("a")) {
        //     String name = file.attr("href");
        //     if (name.endsWith(".Final/")) {
        //         System.out.println(name);

        //         String artifactName = "apicurio-registry-app-" + name.replace(".Final/", ".Final.jar");
        //         String url = repo1 + name + artifactName;

        //         InputStream in = new URL(url).openStream();
        //         Files.copy(in, Paths.get("app/target/" + artifactName), StandardCopyOption.REPLACE_EXISTING);

        //         prevConfigurations.put(name.replace(".Final/", ".Final"), extractConfigurations("app/target/" + artifactName));
        //     }
        // }

        // TODO: include all the relevant jars, to be determined
        // TODO: make the current version configurable
        // Extract configuration from Jandex
        allConfiguration = extractConfigurations("app/target/apicurio-registry-app-" + currentVersion + ".jar");
        for (var config: allConfiguration.values()) {
            config.setAvailableFrom(findOldest(config.getName()));
        }

        // TODO
        // How to handle dynamic RegistryProperties -> we can scan but the configuration is dynamic after it ...


        // STEP 2: BEST EFFORT - parse configurations from reference properties files
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
                                opt.getCategory(),
                                opt.getDescription(),
                                opt.getType(),
                                value,
                                opt.getAvailableFrom()
                        ));
            } else {
                if (key.startsWith("registry.")) {
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
                    // System.out.println("Skipping " + key);
                }
            }
        }

        System.out.println("| name | category | type | default | available-from | description |");
        System.out.println("| --- | --- | --- | --- | --- | --- |");
        for (var config: allConfiguration.values().stream().sorted((o1, o2) -> {
            var cat = o1.getCategory().compareTo(o2.getCategory());
                if (cat != 0) {
                    return cat;
                } else {
                    return o1.getName().compareTo(o2.getName());
                }
            }).collect(Collectors.toList())) {
            System.out.println(config.toMDLine());
        }
    }
}
