package io.apicurio.registry.avro.util;

import io.apicurio.registry.content.TypedContent;
import org.apache.avro.AvroRuntimeException;
import org.apache.avro.Schema;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Single construction point for {@link Schema.Parser} instances, mirroring the role that
 * {@code DocumentBuilderAccessor} and {@code SchemaFactoryAccessor} play for the XML and XSD types. Every
 * Avro parser used by the registry should be obtained here so that parser hardening lives in exactly one
 * place instead of being duplicated across every call site.
 * <p>
 * Unlike the XML and XSD accessors, this class deliberately does <em>not</em> cache the parser in a
 * {@link ThreadLocal}. {@link Schema.Parser} accumulates the named types it has seen and carries them into
 * subsequent {@code parse()} calls on the same instance — behaviour that
 * {@link #newParser(Map)} depends on. A shared or thread-cached parser would therefore leak type
 * definitions between unrelated schemas, letting a schema that references a type it never declares parse
 * successfully because an earlier, unrelated parse happened to define that name. Each caller gets a fresh
 * instance; only the configuration is shared.
 */
public final class AvroParserAccessor {

    private AvroParserAccessor() {
    }

    /**
     * @return a new, hardened parser with no named types pre-registered.
     */
    public static Schema.Parser newParser() {
        return configure(new Schema.Parser());
    }

    /**
     * Returns a new parser seeded with the given referenced schemas, so that the named types they declare are
     * already known by the time the referring schema is parsed. Without this, a schema that references
     * another artifact fails to parse at all.
     * <p>
     * References may depend on one another and the map imposes no ordering, so the ones that fail are
     * re-attempted until a full pass resolves nothing further. Anything still unresolved is left to the
     * caller's own parse, which reports the offending type name.
     *
     * @param resolvedReferences the content of the artifacts referenced by the schema, keyed by reference
     *            name. May be empty or null.
     * @return a new, hardened parser with the resolvable references already registered.
     */
    public static Schema.Parser newParser(Map<String, TypedContent> resolvedReferences) {
        Schema.Parser parser = newParser();
        if (resolvedReferences == null || resolvedReferences.isEmpty()) {
            return parser;
        }

        List<String> pending = new ArrayList<>(resolvedReferences.size());
        for (TypedContent referencedContent : resolvedReferences.values()) {
            pending.add(referencedContent.getContent().content());
        }
        boolean progressed = true;
        while (progressed && !pending.isEmpty()) {
            progressed = false;
            Iterator<String> iter = pending.iterator();
            while (iter.hasNext()) {
                try {
                    parser.parse(iter.next());
                    iter.remove();
                    progressed = true;
                } catch (AvroRuntimeException e) {
                    // Either this reference depends on another one not parsed yet (retried on the next
                    // pass), or it cannot be parsed on its own. Neither is fatal here.
                }
            }
        }
        return parser;
    }

    /**
     * Applies the registry's parser hardening. This is the single place to add future restrictions, for
     * example a nesting-depth limit to bound stack usage on deeply nested schemas.
     * <p>
     * The settings applied here match the Avro library defaults, so this method is behaviour preserving. They
     * are set explicitly so that the registry's posture is stated at one site and cannot be loosened by a
     * change of default in a future Avro release.
     * <p>
     * Do not take Avro's own documentation as the authority on the {@code validateDefaults} default: in the
     * pinned Avro 1.12.1, {@code Schema.java:1432} initialises the field to {@code true} while the
     * {@code getValidateDefaults()} javadoc at {@code Schema.java:1480} claims "False by default". The
     * initialiser is what runs, and it was confirmed against the pinned version rather than the docs, so
     * passing {@code true} here changes nothing today. {@code AvroParserAccessorTest} pins the expected
     * value so a future Avro upgrade that really does flip the default fails loudly instead of silently
     * relaxing validation.
     * <p>
     * Name validation is fixed at construction time in Avro 1.12 ({@code Schema.Parser} holds it in a final
     * field and exposes no setter), so the strict default is inherited from the no-argument constructor
     * above.
     */
    private static Schema.Parser configure(Schema.Parser parser) {
        parser.setValidateDefaults(true);
        return parser;
    }
}
