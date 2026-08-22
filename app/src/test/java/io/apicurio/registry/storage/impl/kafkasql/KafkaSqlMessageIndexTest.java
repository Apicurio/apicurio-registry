package io.apicurio.registry.storage.impl.kafkasql;

import io.apicurio.registry.storage.impl.kafkasql.messages.DeleteOldUsageEvents1Message;
import io.apicurio.registry.storage.impl.kafkasql.messages.RecordUsageEvent1Message;
import io.apicurio.registry.storage.impl.kafkasql.serde.KafkaSqlMessageIndex;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Guards the KafkaSQL journal wire contract.
 * <p>
 * Every mutation in the KafkaSQL storage variant is journaled as a {@link KafkaSqlMessage} and replayed
 * from the beginning of the topic on startup. The message type travels in a Kafka header as the message
 * class's simple name (see {@link AbstractMessage#getKey()} and
 * {@link KafkaSqlSubmitter#MESSAGE_TYPE_HEADER}), and the consumer resolves it back to a class through
 * {@link KafkaSqlMessageIndex#lookup(String)}.
 * <p>
 * That index is hand-maintained, and nothing in the compiler or the CDI container links "class extends
 * AbstractMessage" to "class is registered". A class that is submitted but not registered fails
 * deserialization on the consume side, where the failure is swallowed and the record is discarded, so
 * the mutation is lost silently and on every subsequent replay.
 * <p>
 * This has happened twice: {@code DeleteAllOrphanedContent0Message} was found unregistered in #7810, and
 * the usage-event messages covered here. These tests turn the invariant into a build failure instead of a
 * runtime data-loss bug.
 */
public class KafkaSqlMessageIndexTest {

    private static final String MESSAGES_PACKAGE = "io.apicurio.registry.storage.impl.kafkasql.messages";

    private static final String CLASS_SUFFIX = ".class";

    /**
     * Lower bound on the number of message classes we expect to discover. This exists so the scan below can
     * never pass vacuously: if the classpath layout changes and the scan finds nothing, the test must fail
     * loudly rather than silently stop guarding anything.
     */
    private static final int MINIMUM_EXPECTED_MESSAGE_CLASSES = 50;

    @Test
    public void testEveryMessageClassIsRegisteredInTheIndex() throws Exception {
        List<Class<?>> messageClasses = findConcreteMessageClasses();

        Assertions.assertTrue(messageClasses.size() >= MINIMUM_EXPECTED_MESSAGE_CLASSES,
                "Expected to discover at least " + MINIMUM_EXPECTED_MESSAGE_CLASSES + " KafkaSqlMessage "
                        + "classes in " + MESSAGES_PACKAGE + " but found " + messageClasses.size() + ". "
                        + "The classpath scan is not finding the message classes, so this test is no longer "
                        + "guarding the journal wire contract. Fix the scan before changing this bound.");

        List<String> unregistered = new ArrayList<>();
        for (Class<?> messageClass : messageClasses) {
            if (KafkaSqlMessageIndex.lookup(messageClass.getSimpleName()) == null) {
                unregistered.add(messageClass.getSimpleName());
            }
        }

        Assertions.assertEquals(List.of(), unregistered,
                "The following KafkaSqlMessage classes are not registered in KafkaSqlMessageIndex: "
                        + unregistered + ". Any message submitted to the journal but missing from the index "
                        + "fails to deserialize on the consume side, so the mutation is silently discarded "
                        + "and lost on every replay. Add each class to the static block in "
                        + "KafkaSqlMessageIndex.");
    }

    @Test
    public void testIndexResolvesEachClassToItself() throws Exception {
        for (Class<?> messageClass : findConcreteMessageClasses()) {
            Class<? extends KafkaSqlMessage> resolved = KafkaSqlMessageIndex
                    .lookup(messageClass.getSimpleName());
            if (resolved != null) {
                Assertions.assertEquals(messageClass, resolved,
                        "KafkaSqlMessageIndex resolves '" + messageClass.getSimpleName()
                                + "' to a different class (" + resolved.getName() + "). Two message classes "
                                + "sharing a simple name would collide in the index and silently shadow "
                                + "each other on the journal.");
            }
        }
    }

    @Test
    public void testUsageEventMessagesAreRegistered() {
        // Explicit regression coverage for the two classes this test class was added for. Kept separate
        // from the scan above so the specific defect stays named even if the scan is ever refactored.
        Assertions.assertEquals(RecordUsageEvent1Message.class,
                KafkaSqlMessageIndex.lookup(RecordUsageEvent1Message.class.getSimpleName()),
                "RecordUsageEvent1Message is submitted by KafkaSqlRegistryStorage.recordUsageEvent() but "
                        + "was not registered, so all schema usage telemetry was discarded on the consume "
                        + "side.");

        Assertions.assertEquals(DeleteOldUsageEvents1Message.class,
                KafkaSqlMessageIndex.lookup(DeleteOldUsageEvents1Message.class.getSimpleName()),
                "DeleteOldUsageEvents1Message is submitted by "
                        + "KafkaSqlRegistryStorage.deleteOldUsageEvents() but was not registered, so usage "
                        + "event retention never ran on the KafkaSQL variant.");
    }

    @Test
    public void testMessageKeyTypeResolvesThroughTheIndex() {
        // Exercises the actual wire path end to end: the type written into the Kafka header by
        // AbstractMessage.getKey() must be the same string the consumer looks up.
        KafkaSqlMessage message = RecordUsageEvent1Message.builder().globalId(1L).contentId(2L)
                .clientId("test-client").operation("READ").eventTimestamp(System.currentTimeMillis()).build();

        String messageType = message.getKey().getMessageType();

        Assertions.assertEquals(RecordUsageEvent1Message.class, KafkaSqlMessageIndex.lookup(messageType),
                "The message type written to the '" + KafkaSqlSubmitter.MESSAGE_TYPE_HEADER
                        + "' Kafka header ('" + messageType + "') does not resolve back to its own class "
                        + "through KafkaSqlMessageIndex.");
    }

    /**
     * Finds every concrete {@link KafkaSqlMessage} implementation in the messages package by scanning the
     * classpath directory that holds it. Deliberately dependency-free: the app module has no classpath
     * scanning library on the test classpath.
     */
    private static List<Class<?>> findConcreteMessageClasses() throws Exception {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        Enumeration<URL> resources = classLoader.getResources(MESSAGES_PACKAGE.replace('.', '/'));

        List<Class<?>> messageClasses = new ArrayList<>();
        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            if (!"file".equals(resource.getProtocol())) {
                continue;
            }
            File[] files = new File(resource.toURI()).listFiles();
            if (files == null) {
                continue;
            }
            for (File file : files) {
                String fileName = file.getName();
                // Skip Lombok-generated builder inner classes, which are not message types themselves.
                if (!fileName.endsWith(CLASS_SUFFIX) || fileName.contains("$")) {
                    continue;
                }
                String className = MESSAGES_PACKAGE + "."
                        + fileName.substring(0, fileName.length() - CLASS_SUFFIX.length());
                // initialize=false: resolving these classes must not run their static initializers.
                Class<?> candidate = Class.forName(className, false, classLoader);
                if (KafkaSqlMessage.class.isAssignableFrom(candidate) && !candidate.isInterface()
                        && !Modifier.isAbstract(candidate.getModifiers())) {
                    messageClasses.add(candidate);
                }
            }
        }
        return messageClasses;
    }
}
