package io.apicurio.registry.protobuf.rules.validity;

import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.ServiceDescriptorProto;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Descriptors.DescriptorValidationException;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.squareup.wire.schema.internal.parser.ProtoFileElement;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.protobuf.schema.FileDescriptorUtils;
import io.apicurio.registry.utils.protobuf.schema.ProtobufFile;

import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Runs the security-focused checks that protect Protobuf uploads against schema-injection
 * attacks of the form described in <a href="https://github.com/advisories/GHSA-xq3m-2v4x-88gg">
 * GHSA-xq3m-2v4x-88gg</a>. Two gaps in the default validation flow are covered:
 * <ul>
 *   <li><b>Invalid identifiers in binary uploads.</b> A base64-encoded
 *       {@code FileDescriptorProto} whose message or field names fall outside the protobuf
 *       identifier grammar would otherwise be silently dropped by the Wire text round-trip
 *       that {@code ProtobufContentValidator} performs. Binary inputs are validated directly
 *       with {@code Descriptors.FileDescriptor.buildFrom}, the canonical check shipped with
 *       protobuf-java.</li>
 *   <li><b>Cross-file FQN conflicts.</b> A main artifact that imports two references each
 *       defining the same fully qualified name with different fields is accepted by
 *       protobuf-java (each reference is individually valid). The registry must reject such
 *       uploads because consumers that merge the descriptors at runtime may pick the
 *       attacker-supplied definition.</li>
 * </ul>
 *
 * <p>Comparison operates on the compiled descriptor, not on the text schema. Each source is
 * converted to a {@link FileDescriptorProto} and its typed descriptors
 * ({@link DescriptorProto}, {@link EnumDescriptorProto}, {@link ServiceDescriptorProto}) are
 * walked per FQN. The per-type descriptor carries wire-layout detail (field names, types,
 * tags, labels, oneofs, reserved ranges, options) but no source comments or whitespace, so
 * semantically identical types with cosmetic differences compare equal by construction —
 * no text normalization required. Same-file duplicates and text-form identifier checks are
 * intentionally left to the existing {@code ProtobufContentValidator} flow.</p>
 *
 * <p><b>Out of scope (current limitations).</b> Top-level {@code extend} declarations are
 * not indexed. The threat model of the referenced advisory targets message and enum
 * definitions, so extensions are left to a follow-up.</p>
 */
final class ProtobufFqnConflictDetector {

    static final String MAIN_SOURCE_NAME = "<main>";
    private static final String INVALID_IDENTIFIER_MARKER = "is not a valid identifier";
    private static final String MISSING_NAME_MARKER = "Missing name";

    private ProtobufFqnConflictDetector() {
    }

    /**
     * Runs both the binary-identifier check and the cross-file FQN conflict check over the
     * main artifact and its resolved references. References are walked before main so that
     * a type defined in a reference and flattened into main's descriptor (as happens when
     * the two share a proto package) is still attributed to the reference in error messages.
     *
     * @param level the validity level passed to the surrounding validator; propagated into
     *              any {@link RuleViolationException} raised here
     * @param mainContent the main artifact content; must not be null
     * @param resolvedReferences the resolved reference map (reference name to content);
     *                           may be null or empty
     * @throws RuleViolationException if a binary descriptor is rejected by
     *                                {@code FileDescriptor.buildFrom} with an identifier or
     *                                missing-name error, or if two sources define the same
     *                                fully qualified name with semantically different shapes
     */
    static void assertNoConflicts(ValidityLevel level, TypedContent mainContent,
                                  Map<String, TypedContent> resolvedReferences) throws RuleViolationException {
        validateIdentifiersIfBinary(level, MAIN_SOURCE_NAME, mainContent);
        if (resolvedReferences != null) {
            for (Map.Entry<String, TypedContent> ref : resolvedReferences.entrySet()) {
                validateIdentifiersIfBinary(level, ref.getKey(), ref.getValue());
            }
        }

        Map<String, String> depsText = buildDepsTextMap(resolvedReferences);
        Map<String, Definition> known = new HashMap<>();
        if (resolvedReferences != null) {
            for (Map.Entry<String, TypedContent> ref : resolvedReferences.entrySet()) {
                walkSource(ref.getKey(), ref.getValue(), depsText, known, level);
            }
        }
        walkSource(MAIN_SOURCE_NAME, mainContent, depsText, known, level);
    }

    /**
     * Detects binary {@code FileDescriptorProto} uploads and validates them via
     * {@link Descriptors.FileDescriptor#buildFrom}. This is the only path where identifier
     * grammar is enforced, because the Wire text parser used for text uploads already
     * rejects invalid identifiers at parse time while Wire's binary-to-text round trip
     * silently drops them.
     *
     * @param level the current validity level, used for error reporting
     * @param sourceName the logical name of the content being checked (for error messages)
     * @param content the content to inspect; may be text or binary
     * @throws RuleViolationException if the descriptor contains an invalid or missing
     *                                identifier; other descriptor-level validation errors
     *                                fall through to the normal validation flow
     */
    private static void validateIdentifiersIfBinary(ValidityLevel level, String sourceName,
                                                    TypedContent content) throws RuleViolationException {
        FileDescriptorProto fileProto = tryParseAsBinaryDescriptor(content);
        if (fileProto == null) {
            return;
        }
        try {
            Descriptors.FileDescriptor.buildFrom(fileProto, new Descriptors.FileDescriptor[0], true);
        } catch (DescriptorValidationException dve) {
            String message = dve.getMessage() == null ? "" : dve.getMessage();
            if (message.contains(INVALID_IDENTIFIER_MARKER) || message.contains(MISSING_NAME_MARKER)) {
                throw new RuleViolationException(
                        "Rejected potentially malicious Protobuf identifier in " + sourceName + ": " + message,
                        RuleType.VALIDITY, level.name(), dve);
            }
            // Other validation errors (duplicate symbols, unresolved types, etc.) are reported
            // by the standard validation flow downstream.
        }
    }

    /**
     * Attempts to interpret the raw content as a base64-encoded {@code FileDescriptorProto}.
     * A real text-form proto schema always contains whitespace and punctuation outside the
     * base64 alphabet, so the strict {@link Base64#getDecoder()} used here rejects text
     * uploads without needing a keyword-based heuristic.
     *
     * @param content the candidate content
     * @return the parsed descriptor, or {@code null} if the content is not a binary upload
     */
    private static FileDescriptorProto tryParseAsBinaryDescriptor(TypedContent content) {
        if (content == null || content.getContent() == null) {
            return null;
        }
        String raw = content.getContent().content();
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(trimmed);
        } catch (IllegalArgumentException notBase64) {
            return null;
        }
        try {
            return FileDescriptorProto.parseFrom(decoded);
        } catch (InvalidProtocolBufferException notAFileDescriptor) {
            return null;
        }
    }

    /**
     * Materializes the text schemas for every resolved reference in a single map suitable
     * for {@link FileDescriptorUtils#toFileDescriptorProto}. Binary references are converted
     * to their canonical text form so that the loader, which expects text, can resolve them
     * when another file imports them.
     *
     * @param resolvedReferences the reference map passed to the validator, possibly null
     * @return a mutable, insertion-ordered map of reference name to text schema; empty if
     *         no references were supplied
     */
    private static Map<String, String> buildDepsTextMap(Map<String, TypedContent> resolvedReferences) {
        Map<String, String> deps = new LinkedHashMap<>();
        if (resolvedReferences == null) {
            return deps;
        }
        for (Map.Entry<String, TypedContent> entry : resolvedReferences.entrySet()) {
            String text = ProtobufFile.toProtoFileElement(entry.getValue().getContent().content()).toSchema();
            deps.put(entry.getKey(), text);
        }
        return deps;
    }

    /**
     * Parses a single source (main or a reference) and records every top-level type and
     * service it contributes to the FQN map, recursing into nested types. Comparison is
     * performed on the compiled {@link DescriptorProto} / {@link EnumDescriptorProto} /
     * {@link ServiceDescriptorProto}, not on text, so whitespace and comments are irrelevant.
     *
     * @param sourceName the logical name of the source (used in error messages and to scope
     *                   duplicate detection within the same file to the existing validator)
     * @param content the source content, text or binary
     * @param depsText text schemas of every reference, for import resolution
     * @param known the FQN map populated across all sources; earlier entries are compared
     *              against later redefinitions
     * @param level the current validity level, used for error reporting
     * @throws RuleViolationException if a definition in this source conflicts with an
     *                                earlier definition in a different source
     */
    private static void walkSource(String sourceName, TypedContent content,
                                   Map<String, String> depsText,
                                   Map<String, Definition> known, ValidityLevel level)
            throws RuleViolationException {
        FileDescriptorProto fdp = buildFileDescriptorProto(sourceName, content, depsText);
        if (fdp == null) {
            // Could not compile the source into a FileDescriptorProto. The surrounding
            // validator flow will surface a concrete syntax / semantic error for it.
            return;
        }
        String pkg = fdp.hasPackage() ? fdp.getPackage() : "";
        for (DescriptorProto m : fdp.getMessageTypeList()) {
            recordMessage(m, pkg, "", sourceName, known, level);
        }
        for (EnumDescriptorProto e : fdp.getEnumTypeList()) {
            record(toFqn(pkg, e.getName()), e, sourceName, known, level);
        }
        for (ServiceDescriptorProto s : fdp.getServiceList()) {
            record(toFqn(pkg, s.getName()), s, sourceName, known, level);
        }
    }

    /**
     * Produces a {@link FileDescriptorProto} for the given source, using the binary form
     * directly when present or running the source text through the shared
     * {@link FileDescriptorUtils} converter otherwise. Conversion failure returns
     * {@code null} so the surrounding validator can surface the real error.
     *
     * <p>Only the imports declared by the source are passed to the converter, even though
     * the full dependency map is available. Passing unreferenced deps would cause
     * {@link FileDescriptorUtils#toFileDescriptorProto} to flatten all types from files
     * that share the source's package into the resulting descriptor, which would wrongly
     * attribute a conflicting reference's types to whichever file is currently being
     * walked.</p>
     */
    private static FileDescriptorProto buildFileDescriptorProto(String sourceName, TypedContent content,
                                                                Map<String, String> allDepsText) {
        FileDescriptorProto binary = tryParseAsBinaryDescriptor(content);
        if (binary != null) {
            return binary;
        }
        try {
            ProtoFileElement pfe = ProtobufFile.toProtoFileElement(content.getContent().content());
            return FileDescriptorUtils.toFileDescriptorProto(pfe.toSchema(), sourceName,
                    Optional.ofNullable(pfe.getPackageName()), filterToImports(allDepsText, pfe));
        } catch (RuntimeException conversionFailed) {
            return null;
        }
    }

    /**
     * Selects, from the complete dependency-text map, only the entries matching the
     * imports declared by {@code pfe}. Both regular and public imports are honored.
     */
    private static Map<String, String> filterToImports(Map<String, String> allDepsText,
                                                       ProtoFileElement pfe) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String imp : pfe.getImports()) {
            if (allDepsText.containsKey(imp)) {
                result.put(imp, allDepsText.get(imp));
            }
        }
        for (String imp : pfe.getPublicImports()) {
            if (allDepsText.containsKey(imp)) {
                result.put(imp, allDepsText.get(imp));
            }
        }
        return result;
    }

    /**
     * Records a single message and recursively its nested types. Nested messages and nested
     * enums are accessed through two typed getters on {@link DescriptorProto} — both return
     * concrete descriptor lists, so no runtime type discrimination is required.
     *
     * @param message the compiled descriptor being recorded
     * @param packageName the proto package of the enclosing file
     * @param scope the dotted scope path accumulated from outer messages (empty for top-level)
     * @param sourceName the logical source name that contributed this type
     * @param known the shared FQN map
     * @param level the current validity level, used for error reporting
     * @throws RuleViolationException if a conflicting definition is discovered
     */
    private static void recordMessage(DescriptorProto message, String packageName, String scope,
                                      String sourceName, Map<String, Definition> known,
                                      ValidityLevel level) throws RuleViolationException {
        String scopedName = scope.isEmpty() ? message.getName() : scope + "." + message.getName();
        record(toFqn(packageName, scopedName), shallow(message), sourceName, known, level);
        for (DescriptorProto nested : message.getNestedTypeList()) {
            recordMessage(nested, packageName, scopedName, sourceName, known, level);
        }
        for (EnumDescriptorProto nestedEnum : message.getEnumTypeList()) {
            record(toFqn(packageName, scopedName + "." + nestedEnum.getName()), nestedEnum,
                    sourceName, known, level);
        }
    }

    /**
     * Produces a shallow copy of a message descriptor with its nested messages and nested
     * enums cleared. Without this, {@link Message#equals(Object)} would recurse into nested
     * types, causing an outer message to be flagged as conflicting when the real
     * disagreement is on an inner type — and the error would point at the wrong FQN. Each
     * nested type is recorded separately, so its own equality check catches the conflict
     * with the correct attribution.
     */
    private static DescriptorProto shallow(DescriptorProto message) {
        return message.toBuilder().clearNestedType().clearEnumType().build();
    }

    /**
     * Records one FQN definition and, if a prior definition exists in a different source,
     * rejects the upload when the two compiled descriptors are not equal. Same-source
     * redefinitions are intentionally ignored here because the existing Wire / protobuf-java
     * validators already surface same-file duplicates.
     *
     * @param fqn the fully qualified name being recorded
     * @param descriptor the compiled descriptor for the type (shallow for messages)
     * @param sourceName the logical source name that contributed this definition
     * @param known the shared FQN map
     * @param level the current validity level, used for error reporting
     * @throws RuleViolationException if {@code fqn} was already defined in a different
     *                                source with a different descriptor
     */
    private static void record(String fqn, Message descriptor, String sourceName,
                               Map<String, Definition> known, ValidityLevel level)
            throws RuleViolationException {
        Definition existing = known.get(fqn);
        if (existing == null) {
            known.put(fqn, new Definition(descriptor, sourceName));
            return;
        }
        if (existing.sourceName.equals(sourceName)) {
            return;
        }
        if (!existing.descriptor.equals(descriptor)) {
            throw new RuleViolationException(
                    "Conflicting Protobuf type definition detected for " + fqn
                            + " (first defined in " + existing.sourceName
                            + ", redefined in " + sourceName + ").",
                    RuleType.VALIDITY, level.name(), Collections.emptySet());
        }
    }

    /**
     * Joins a proto package and a dotted scope path into a fully qualified name. Either
     * side may be empty; the method collapses separators accordingly.
     */
    private static String toFqn(String packageName, String scopeName) {
        if (packageName == null || packageName.isEmpty()) {
            return scopeName;
        }
        if (scopeName == null || scopeName.isEmpty()) {
            return packageName;
        }
        return packageName + "." + scopeName;
    }

    private static final class Definition {
        final Message descriptor;
        final String sourceName;

        Definition(Message descriptor, String sourceName) {
            this.descriptor = descriptor;
            this.sourceName = sourceName;
        }
    }
}
