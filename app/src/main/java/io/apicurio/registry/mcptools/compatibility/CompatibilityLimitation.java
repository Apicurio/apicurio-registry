package io.apicurio.registry.mcptools.compatibility;

/**
 * Something that prevents a comparison from fully deciding whether two tools are compatible.
 *
 * @param code what kind of limitation this is
 * @param side the tool schema it refers to
 * @param node JSON Pointer to the schema node it covers, used to decide which mismatches it
 *        invalidates when its code {@link LimitationCode#coversSubtree() covers a subtree}
 * @param pointer JSON Pointer into the tool document that locates the cause, such as the
 *        unsupported keyword itself
 * @param message human readable description of the limitation
 */
public record CompatibilityLimitation(LimitationCode code, SchemaSide side, String node,
        String pointer, String message) {
}
