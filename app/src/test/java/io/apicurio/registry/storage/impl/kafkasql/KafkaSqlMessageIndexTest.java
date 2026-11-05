package io.apicurio.registry.storage.impl.kafkasql;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.apicurio.registry.storage.impl.kafkasql.messages.DeleteOldUsageEvents1Message;
import io.apicurio.registry.storage.impl.kafkasql.messages.RecordUsageEvent1Message;
import io.apicurio.registry.storage.impl.kafkasql.serde.KafkaSqlMessageIndex;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * KafkaSQL replays its journal on every start and resolves each record's type through
 * {@link KafkaSqlMessageIndex}. A message class missing from that hand-maintained list fails to
 * deserialize on the consume side, where the failure is swallowed, so the mutation is lost silently.
 */
class KafkaSqlMessageIndexTest {

    private static final String MESSAGES_PACKAGE = "io.apicurio.registry.storage.impl.kafkasql.messages";

    @Test
    void everyMessageClassIsRegisteredInTheIndex() {
        List<JavaClass> messageClasses = messageClasses();

        List<String> unregistered = messageClasses.stream().map(JavaClass::getSimpleName)
                .filter(name -> KafkaSqlMessageIndex.lookup(name) == null).sorted().toList();

        Assertions.assertEquals(List.of(), unregistered,
                "KafkaSqlMessage classes missing from KafkaSqlMessageIndex");
    }

    @Test
    void scanFindsTheMessageClassesItGuards() {
        List<String> names = messageClasses().stream().map(JavaClass::getSimpleName).toList();

        Assertions.assertTrue(names.contains(RecordUsageEvent1Message.class.getSimpleName()),
                "the scan no longer finds the usage messages, so it does not guard the index");
        Assertions.assertTrue(names.contains(DeleteOldUsageEvents1Message.class.getSimpleName()),
                "the scan no longer finds the usage messages, so it does not guard the index");
    }

    private static List<JavaClass> messageClasses() {
        return new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(MESSAGES_PACKAGE).stream()
                .filter(c -> c.isAssignableTo(KafkaSqlMessage.class))
                .filter(c -> !c.isInterface() && !c.getModifiers().contains(JavaModifier.ABSTRACT))
                .filter(c -> !c.getName().contains("$")).toList();
    }
}
