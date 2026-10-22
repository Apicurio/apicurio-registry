package io.apicurio.registry.protobuf.rules.validity;

import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.ServiceDescriptorProto;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Descriptors.DescriptorValidationException;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.squareup.wire.schema.Location;
import com.squareup.wire.schema.internal.parser.ProtoFileElement;
import com.squareup.wire.schema.internal.parser.ProtoParser;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.protobuf.schema.FileDescriptorUtils;

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
    /**
     * Substring markers pulled from {@code com.google.protobuf.Descriptors.DescriptorValidationException}
     * messages. protobuf-java does not expose error codes or typed sub-exceptions for these
     * two specific failures, so string matching is the only way to discriminate an
     * identifier-grammar rejection from other descriptor-build failures (duplicate symbols,
     * unresolved types, and so on) that should fall through to the normal validation flow.
     *
     * <p>These constants are therefore coupled to protobuf-java's internal message wording.
     * A dependency bump must re-verify them. Two regression tests —
     * {@code ProtobufContentValidatorTest#testRejectsUnsafeIdentifierInBinaryDescriptor} and
     * {@code ProtobufContentValidatorTest#testRejectsMissingNameInBinaryDescriptor} — assert
     * that the corresponding descriptors are rejected end-to-end, so if the message wording
     * changes and these markers stop matching, both tests will fail loudly in CI.
     */
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
     * @return an insertion-ordered map of reference name to its canonical text schema,
     *         empty if no references were supplied. The surrounding validator can reuse
     *         this map for its own dependency-aware parse step instead of re-running the
     *         text conversion over every reference.
     * @throws RuleViolationException if a binary descriptor is rejected by
     *                                {@code FileDescriptor.buildFrom} with an identifier or
     *                                missing-name error, if a reference cannot be parsed
     *                                at all, or if two sources define the same fully
     *                                qualified name with semantically different shapes.
     *                                This detector wraps every other failure that could
     *                                arise from its inputs (malformed base64, unreadable
     *                                descriptors, parser errors) into a
     *                                {@code RuleViolationException}, so callers do not
     *                                need to catch raw {@code RuntimeException}s from it.
     */
    static Map<String, String> assertNoConflicts(ValidityLevel level, TypedContent mainContent,
                                                 Map<String, TypedContent> resolvedReferences) throws RuleViolationException {
        // One-shot per-source binary parse: if a source is a base64-encoded
        // FileDescriptorProto, decode and parse it here exactly once. Both the identifier
        // grammar check below and the FQN walk later on reuse the cached descriptor
        // instead of re-decoding the same bytes.
        Map<String, FileDescriptorProto> binaryDescriptors = new HashMap<>();
        FileDescriptorProto mainBinary = tryParseAsBinaryDescriptor(mainContent);
        if (mainBinary != null) {
            binaryDescriptors.put(MAIN_SOURCE_NAME, mainBinary);
        }
        if (resolvedReferences != null) {
            for (Map.Entry<String, TypedContent> ref : resolvedReferences.entrySet()) {
                FileDescriptorProto refBinary = tryParseAsBinaryDescriptor(ref.getValue());
                if (refBinary != null) {
                    binaryDescriptors.put(ref.getKey(), refBinary);
                }
            }
        }

        validateIdentifiersIfBinary(level, MAIN_SOURCE_NAME, binaryDescriptors.get(MAIN_SOURCE_NAME));
        if (resolvedReferences != null) {
            for (Map.Entry<String, TypedContent> ref : resolvedReferences.entrySet()) {
                validateIdentifiersIfBinary(level, ref.getKey(), binaryDescriptors.get(ref.getKey()));
            }
        }

        Map<String, String> depsText = buildDepsTextMap(level, resolvedReferences, binaryDescriptors);
        Map<String, Definition> known = new HashMap<>();
        if (resolvedReferences != null) {
            for (Map.Entry<String, TypedContent> ref : resolvedReferences.entrySet()) {
                walkSource(ref.getKey(), ref.getValue(), binaryDescriptors.get(ref.getKey()),
                        depsText, known, level);
            }
        }
        walkSource(MAIN_SOURCE_NAME, mainContent, binaryDescriptors.get(MAIN_SOURCE_NAME),
                depsText, known, level);
        return depsText;
    }

    /**
     * Validates identifier grammar on a pre-parsed binary {@code FileDescriptorProto} via
     * {@link Descriptors.FileDescriptor#buildFrom}. This is the only path where identifier
     * grammar is enforced, because the Wire text parser used for text uploads already
     * rejects invalid identifiers at parse time while Wire's binary-to-text round trip
     * silently drops them.
     *
     * @param level the current validity level, used for error reporting
     * @param sourceName the logical name of the content being checked (for error messages)
     * @param fileProto the pre-parsed binary descriptor, or {@code null} if the source is
     *                  not a binary upload (in which case this method is a no-op)
     * @throws RuleViolationException if the descriptor contains an invalid or missing
     *                                identifier; other descriptor-level validation errors
     *                                fall through to the normal validation flow
     */
    private static void validateIdentifiersIfBinary(ValidityLevel level, String sourceName,
                                                    FileDescriptorProto fileProto) throws RuleViolationException {
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
     * for {@link FileDescriptorUtils#toFileDescriptorProto}. References that were already
     * parsed as binary descriptors upstream (see {@link #assertNoConflicts}) are converted
     * to their canonical text form without re-parsing; other references are put through
     * the Wire text-form parser.
     *
     * <p>This method does not catch {@link RuntimeException} in aggregate. Each downstream
     * call has a narrow, typed catch on the specific declared failure mode
     * ({@link IllegalArgumentException} from {@code Base64.getDecoder().decode(...)} via
     * {@link #tryParseAsText}, or the text-parse fallback returning {@code null}). A
     * reference that cannot be interpreted as either text or binary protobuf content is
     * converted here into a {@link RuleViolationException} that names the offending
     * reference — preserving the {@link #assertNoConflicts} contract that only
     * {@code RuleViolationException} escapes this detector while still surfacing an
     * actionable, domain-level error rather than leaking a library exception class.</p>
     *
     * @param level the current validity level, used when constructing a
     *              {@link RuleViolationException} for an unreadable reference
     * @param resolvedReferences the reference map passed to the validator, possibly null
     * @param binaryDescriptors the per-source cache of already-decoded binary descriptors
     *                          populated by {@code assertNoConflicts}
     * @return a mutable, insertion-ordered map of reference name to text schema; empty if
     *         no references were supplied
     * @throws RuleViolationException if any reference is neither valid text proto nor a
     *                                valid base64-encoded {@code FileDescriptorProto}
     */
    private static Map<String, String> buildDepsTextMap(ValidityLevel level,
                                                        Map<String, TypedContent> resolvedReferences,
                                                        Map<String, FileDescriptorProto> binaryDescriptors)
            throws RuleViolationException {
        Map<String, String> deps = new LinkedHashMap<>();
        if (resolvedReferences == null) {
            return deps;
        }
        for (Map.Entry<String, TypedContent> entry : resolvedReferences.entrySet()) {
            String refName = entry.getKey();
            FileDescriptorProto cachedBinary = binaryDescriptors.get(refName);
            if (cachedBinary != null) {
                // We already parsed this reference as a binary descriptor once. Convert it
                // to text form without another parse round trip.
                deps.put(refName, FileDescriptorUtils.fileDescriptorToProtoFile(cachedBinary).toSchema());
                continue;
            }
            ProtoFileElement textElement = tryParseAsText(entry.getValue());
            if (textElement == null) {
                throw new RuleViolationException(
                        "Failed to parse Protobuf reference '" + refName
                                + "': content is neither a valid text-form proto schema nor a valid "
                                + "base64-encoded FileDescriptorProto.",
                        RuleType.VALIDITY, level.name(), Collections.emptySet());
            }
            deps.put(refName, textElement.toSchema());
        }
        return deps;
    }

    /**
     * Attempts to parse the raw content as a Wire text-form {@link ProtoFileElement}.
     * Mirror of {@link #tryParseAsBinaryDescriptor}: the single scoped {@code RuntimeException}
     * catch is a "try next strategy" signal, not an error-hiding wrapper — it is bounded
     * to exactly the {@code ProtoParser.readProtoFile()} invocation and only converts
     * failure into a {@code null} return. The caller decides whether to try another
     * representation or to raise a domain-level violation.
     *
     * @param content the candidate content; may be {@code null}
     * @return the parsed element, or {@code null} if the content is not valid text proto
     */
    private static ProtoFileElement tryParseAsText(TypedContent content) {
        if (content == null || content.getContent() == null) {
            return null;
        }
        String raw = content.getContent().content();
        if (raw == null) {
            return null;
        }
        try {
            return new ProtoParser(Location.get(""), raw.toCharArray()).readProtoFile();
        } catch (RuntimeException notText) {
            return null;
        }
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
     * @param preParsedBinary the already-decoded binary descriptor for this source if the
     *                        caller determined it was a binary upload; {@code null} forces
     *                        the text-parse path
     * @param depsText text schemas of every reference, for import resolution
     * @param known the FQN map populated across all sources; earlier entries are compared
     *              against later redefinitions
     * @param level the current validity level, used for error reporting
     * @throws RuleViolationException if a definition in this source conflicts with an
     *                                earlier definition in a different source
     */
    private static void walkSource(String sourceName, TypedContent content,
                                   FileDescriptorProto preParsedBinary,
                                   Map<String, String> depsText,
                                   Map<String, Definition> known, ValidityLevel level)
            throws RuleViolationException {
        FileDescriptorProto fdp = buildFileDescriptorProto(sourceName, content, preParsedBinary, depsText);
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
            recordDefinition(toFqn(pkg, e.getName()), e, sourceName, known, level);
        }
        for (ServiceDescriptorProto s : fdp.getServiceList()) {
            recordDefinition(toFqn(pkg, s.getName()), s, sourceName, known, level);
        }
    }

    /**
     * Produces a {@link FileDescriptorProto} for the given source, returning the pre-parsed
     * binary descriptor directly when one was supplied or running the source text through
     * the shared {@link FileDescriptorUtils} converter otherwise. Returns {@code null}
     * when neither path can produce a descriptor, so the caller ({@link #walkSource})
     * skips this source and the surrounding validator surfaces the real error.
     *
     * <p>Only the imports declared by the source are passed to the converter, even though
     * the full dependency map is available. Passing unreferenced deps would cause
     * {@link FileDescriptorUtils#toFileDescriptorProto} to flatten all types from files
     * that share the source's package into the resulting descriptor, which would wrongly
     * attribute a conflicting reference's types to whichever file is currently being
     * walked.</p>
     *
     * <p>The one {@code RuntimeException} catch in this method is a narrow, deliberate
     * accommodation of {@link FileDescriptorUtils#toFileDescriptorProto}'s contract:
     * that method has no declared throws and its chosen failure signal is
     * {@code throw new RuntimeException(e)} wrapping every underlying cause. We cannot
     * catch narrower types than {@code RuntimeException} here because the library does
     * not expose them. The catch is scoped to that single call; the text parse happens
     * through {@link #tryParseAsText}, which returns {@code null} on failure instead of
     * relying on an exception.</p>
     */
    private static FileDescriptorProto buildFileDescriptorProto(String sourceName, TypedContent content,
                                                                FileDescriptorProto preParsedBinary,
                                                                Map<String, String> allDepsText) {
        if (preParsedBinary != null) {
            return preParsedBinary;
        }
        ProtoFileElement pfe = tryParseAsText(content);
        if (pfe == null) {
            return null;
        }
        try {
            return FileDescriptorUtils.toFileDescriptorProto(pfe.toSchema(), sourceName,
                    Optional.ofNullable(pfe.getPackageName()), filterToImports(allDepsText, pfe));
        } catch (RuntimeException toFileDescriptorProtoFailed) {
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
        recordDefinition(toFqn(packageName, scopedName), shallow(message), sourceName, known, level);
        for (DescriptorProto nested : message.getNestedTypeList()) {
            recordMessage(nested, packageName, scopedName, sourceName, known, level);
        }
        for (EnumDescriptorProto nestedEnum : message.getEnumTypeList()) {
            recordDefinition(toFqn(packageName, scopedName + "." + nestedEnum.getName()), nestedEnum,
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
    private static void recordDefinition(String fqn, Message descriptor, String sourceName,
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
