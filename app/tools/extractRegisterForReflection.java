///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS org.reflections:reflections:0.10.2
//DEPS io.apicurio:apicurio-data-models:1.1.26

import java.util.Set;
import org.reflections.Reflections;

import static org.reflections.scanners.Scanners.SubTypes;

public class extractRegisterForReflection {

    public static void main(String... args) {
        Reflections reflections = new Reflections("io.apicurio");

        Set<Class<?>> subTypes = reflections.get(SubTypes.of(io.apicurio.datamodels.core.validation.ValidationRule.class).asClass());
        subTypes.addAll(reflections.get(SubTypes.of(io.apicurio.datamodels.core.models.Node.class).asClass()));

        subTypes
            .stream()
            .map(t -> t.getCanonicalName() + ".class")
            .sorted()
            .forEach(s -> System.out.println(s));
    }
}
