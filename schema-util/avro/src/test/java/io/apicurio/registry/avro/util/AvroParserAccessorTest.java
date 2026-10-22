package io.apicurio.registry.avro.util;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.types.ContentTypes;
import org.apache.avro.Schema;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AvroParserAccessorTest {

    private static final Pattern DIRECT_CONSTRUCTION = Pattern.compile("new\\s+Schema\\.Parser\\s*\\(");

    private static final String ADDRESS = "{\"type\":\"record\",\"name\":\"Address\","
            + "\"namespace\":\"com.example\",\"fields\":[{\"name\":\"street\",\"type\":\"string\"}]}";

    private static final String CITY = "{\"type\":\"record\",\"name\":\"City\","
            + "\"namespace\":\"com.example\",\"fields\":[{\"name\":\"name\",\"type\":\"string\"}]}";

    /** Depends on City, so it cannot be parsed before City has been. */
    private static final String ADDRESS_WITH_CITY = "{\"type\":\"record\",\"name\":\"Address\","
            + "\"namespace\":\"com.example\",\"fields\":[{\"name\":\"city\",\"type\":\"com.example.City\"}]}";

    private static final String PERSON_REFERENCING_ADDRESS = "{\"type\":\"record\",\"name\":\"Person\","
            + "\"namespace\":\"com.example\",\"fields\":[{\"name\":\"address\",\"type\":\"com.example.Address\"}]}";

    private static TypedContent content(String schema) {
        return TypedContent.create(ContentHandle.create(schema), ContentTypes.APPLICATION_JSON);
    }

    /**
     * The guard that fails on the buggy code. Before the fix this module constructed Schema.Parser at eight
     * independent sites, so any hardening had to be repeated eight times and one could silently be missed.
     * This asserts the invariant itself: within this module's production sources, AvroParserAccessor is the
     * only class allowed to construct a parser.
     * <p>
     * Scope limitation: this walks only the current module, so it does not cover the two call sites that were
     * also routed through the accessor but live in the app module
     * ({@code contracts/tags/AvroTagExtractor} and {@code ccompat/rest/v7/impl/SchemaFormatService}, the
     * latter on the Confluent-compatibility path). A regression there would not fail this test. Extending the
     * same guard to the app module is worth doing separately; do not read a pass here as repository-wide
     * coverage.
     */
    @Test
    void noProductionCodeInThisModuleConstructsSchemaParserDirectly() throws IOException {
        // Surefire runs with the working directory set to the module base directory.
        Path mainSources = Paths.get("src", "main", "java");
        assertTrue(Files.isDirectory(mainSources),
                "expected to run from the module base directory, but " + mainSources.toAbsolutePath()
                        + " does not exist");

        List<Path> offenders;
        try (Stream<Path> sources = Files.walk(mainSources)) {
            offenders = sources.filter(p -> p.toString().endsWith(".java"))
                    // The accessor is the one place allowed to construct a parser.
                    .filter(p -> !p.getFileName().toString().equals("AvroParserAccessor.java"))
                    .filter(p -> DIRECT_CONSTRUCTION.matcher(readString(p)).find()).toList();
        }

        assertTrue(offenders.isEmpty(),
                "Schema.Parser must be obtained from AvroParserAccessor so that parser hardening lives in "
                        + "one place. Direct construction found in: " + offenders);
    }

    @Test
    void newParserAppliesHardening() {
        // Explicitly pinned rather than inherited, so a change of Avro default cannot loosen validation.
        assertTrue(AvroParserAccessor.newParser().getValidateDefaults());

        // Strict name validation is inherited from the no-arg constructor and must stay in effect.
        assertThrows(Exception.class, () -> AvroParserAccessor.newParser()
                .parse("{\"type\":\"record\",\"name\":\"has-dash\",\"fields\":[]}"));

        // A bad default value must still be rejected.
        assertThrows(Exception.class, () -> AvroParserAccessor.newParser().parse(
                "{\"type\":\"record\",\"name\":\"R\",\"fields\":[{\"name\":\"f\",\"type\":\"int\",\"default\":\"nope\"}]}"));
    }

    /**
     * The reason this accessor must not cache parsers the way DocumentBuilderAccessor does: a parser
     * accumulates named types, so a reused instance would let a schema reference a type it never declares.
     */
    @Test
    void eachCallReturnsAnIndependentParser() {
        Schema.Parser first = AvroParserAccessor.newParser();
        Schema.Parser second = AvroParserAccessor.newParser();
        assertNotSame(first, second);

        first.parse(ADDRESS);
        assertTrue(first.getTypes().containsKey("com.example.Address"));

        // The second parser must not have inherited Address from the first.
        assertFalse(second.getTypes().containsKey("com.example.Address"));
        assertThrows(Exception.class, () -> second.parse(PERSON_REFERENCING_ADDRESS));
    }

    @Test
    void seededParserResolvesReferences() {
        Map<String, TypedContent> references = Map.of("com.example.Address", content(ADDRESS));

        Schema parsed = AvroParserAccessor.newParser(references).parse(PERSON_REFERENCING_ADDRESS);

        assertEquals("Person", parsed.getName());
        assertEquals(Schema.Type.RECORD, parsed.getField("address").schema().getType());
    }

    /**
     * Interdependent references in an unlucky map order. The retry-to-fixpoint pass in the accessor is what
     * makes this work regardless of iteration order; a single naive pass would fail here.
     */
    @Test
    void seededParserResolvesInterdependentReferencesInAnyOrder() {
        // LinkedHashMap so Address (which needs City) is offered before City.
        Map<String, TypedContent> references = new LinkedHashMap<>();
        references.put("com.example.Address", content(ADDRESS_WITH_CITY));
        references.put("com.example.City", content(CITY));

        Schema parsed = AvroParserAccessor.newParser(references).parse(PERSON_REFERENCING_ADDRESS);

        assertEquals("Person", parsed.getName());
    }

    @Test
    void seededParserToleratesNullAndEmptyReferences() {
        assertEquals("Address",
                AvroParserAccessor.newParser((Map<String, TypedContent>) null).parse(ADDRESS).getName());
        assertEquals("Address", AvroParserAccessor.newParser(Map.of()).parse(ADDRESS).getName());
    }

    /**
     * An unresolvable reference must not mask the real failure: the main parse still reports the type it
     * cannot find.
     */
    @Test
    void unresolvableReferenceStillFailsTheMainParse() {
        Map<String, TypedContent> references = Map.of("broken", content("{ not a schema }"));

        Exception ex = assertThrows(Exception.class,
                () -> AvroParserAccessor.newParser(references).parse(PERSON_REFERENCING_ADDRESS));
        assertTrue(ex.getMessage().contains("com.example.Address"),
                "expected the offending type name in the message, got: " + ex.getMessage());
    }

    private static String readString(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
