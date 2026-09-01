package io.apicurio.registry.rules.validity;

import com.fasterxml.jackson.databind.JsonNode;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.util.ContentTypeUtil;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.rules.violation.RuleViolation;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.types.RuleType;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Content validator for MCP (Model Context Protocol) server definition artifacts (<code>server.json</code>).
 *
 * Validation levels:
 * <ul>
 * <li>NONE: No validation</li>
 * <li>SYNTAX_ONLY: Validates that the content is valid JSON and is an object</li>
 * <li>FULL: Validates the identity fields required by the MCP registry (name, version), the reverse-DNS
 * shape of the name, and the structure of the optional repository, packages, remotes and icons
 * blocks.</li>
 * </ul>
 *
 * @see <a href="https://github.com/modelcontextprotocol/registry">MCP Registry</a>
 */
public class McpServerContentValidator implements ContentValidator {

    /**
     * A server name is a reverse-DNS namespace and a server id separated by exactly one slash, e.g.
     * <code>io.github.user/weather</code>. Neither half may be empty and neither may contain a slash, which
     * also rules out path traversal when the name is used to address a group and artifact.
     */
    public static final Pattern SERVER_NAME_PATTERN = Pattern
            .compile("^[a-zA-Z0-9][a-zA-Z0-9._-]*/[a-zA-Z0-9][a-zA-Z0-9._-]*$");

    /**
     * The transports an MCP server may declare, for both packages and remotes.
     */
    private static final List<String> TRANSPORT_TYPES = List.of("stdio", "streamable-http", "sse");

    @Override
    public void validate(ValidityLevel level, TypedContent content,
            Map<String, TypedContent> resolvedReferences) throws RuleViolationException {

        if (level == ValidityLevel.NONE) {
            return;
        }

        Set<RuleViolation> violations = new HashSet<>();

        try {
            JsonNode tree = ContentTypeUtil.parseJson(content.getContent());

            if (!tree.isObject()) {
                throw new RuleViolationException("MCP server definition must be a JSON object",
                        RuleType.VALIDITY, level.name(), Collections.singleton(
                                new RuleViolation("MCP server definition must be a JSON object", "")));
            }

            if (level == ValidityLevel.SYNTAX_ONLY) {
                return;
            }

            validateNameField(tree, violations);
            validateVersionField(tree, violations);
            JsonValidationUtils.validateOptionalString(tree, "title", violations);
            JsonValidationUtils.validateOptionalString(tree, "description", violations);
            validateRepositoryField(tree, violations);
            validatePackagesField(tree, violations);
            validateRemotesField(tree, violations);
            validateIconsField(tree, violations);

            if (!violations.isEmpty()) {
                throw new RuleViolationException("Invalid MCP server definition", RuleType.VALIDITY,
                        level.name(), violations);
            }

        } catch (RuleViolationException e) {
            throw e;
        } catch (Exception e) {
            throw new RuleViolationException("Invalid MCP server definition JSON: " + e.getMessage(),
                    RuleType.VALIDITY, level.name(), e);
        }
    }

    private void validateNameField(JsonNode tree, Set<RuleViolation> violations) {
        if (!tree.has("name")) {
            violations.add(new RuleViolation("MCP server definition must have a 'name' field", "/name"));
            return;
        }
        JsonNode name = tree.get("name");
        if (!name.isTextual()) {
            violations.add(new RuleViolation("'name' field must be a string", "/name"));
            return;
        }
        if (!SERVER_NAME_PATTERN.matcher(name.asText()).matches()) {
            violations.add(new RuleViolation(
                    "'name' must be a reverse-DNS namespace and a server id separated by a single slash,"
                            + " for example 'io.github.user/weather'",
                    "/name"));
        }
    }

    private void validateVersionField(JsonNode tree, Set<RuleViolation> violations) {
        if (!tree.has("version")) {
            violations.add(
                    new RuleViolation("MCP server definition must have a 'version' field", "/version"));
        } else if (!tree.get("version").isTextual()) {
            violations.add(new RuleViolation("'version' field must be a string", "/version"));
        } else if (tree.get("version").asText().trim().isEmpty()) {
            violations.add(new RuleViolation("'version' field must not be empty", "/version"));
        }
    }

    private void validateRepositoryField(JsonNode tree, Set<RuleViolation> violations) {
        if (!tree.has("repository")) {
            return;
        }
        JsonNode repository = tree.get("repository");
        if (!repository.isObject()) {
            violations.add(new RuleViolation("'repository' field must be an object", "/repository"));
            return;
        }
        if (!repository.has("url")) {
            violations.add(new RuleViolation("'repository' must have a 'url' field", "/repository/url"));
        } else if (!repository.get("url").isTextual()) {
            violations.add(new RuleViolation("'repository.url' must be a string", "/repository/url"));
        } else {
            JsonValidationUtils.validateHttpUrl(repository.get("url").asText(), "/repository/url",
                    violations);
        }
        validateOptionalStringAt(repository, "source", "/repository", violations);
        validateOptionalStringAt(repository, "id", "/repository", violations);
        validateOptionalStringAt(repository, "subfolder", "/repository", violations);
    }

    /**
     * {@link JsonValidationUtils#validateOptionalString} hardcodes {@code "/" + fieldName} as the
     * violation's JSON pointer, which is only correct when {@code node} is the document root. For a nested
     * object like {@code repository}, that reports e.g. {@code /source} instead of {@code /repository/source}
     * - wrong, and user-visible, since the pointer is a published field on {@code RuleViolationCause} that
     * reaches API clients. This takes the parent path explicitly, the same way {@link #requireTextualField}
     * already does for packages, remotes and icons.
     */
    private void validateOptionalStringAt(JsonNode node, String field, String parentPath,
            Set<RuleViolation> violations) {
        if (node.has(field) && !node.get(field).isTextual()) {
            violations.add(
                    new RuleViolation("'" + field + "' field must be a string", parentPath + "/" + field));
        }
    }

    private void validatePackagesField(JsonNode tree, Set<RuleViolation> violations) {
        if (!tree.has("packages")) {
            return;
        }
        JsonNode packages = tree.get("packages");
        if (!packages.isArray()) {
            violations.add(new RuleViolation("'packages' field must be an array", "/packages"));
            return;
        }
        for (int i = 0; i < packages.size(); i++) {
            String path = "/packages/" + i;
            JsonNode pkg = packages.get(i);
            if (!pkg.isObject()) {
                violations.add(new RuleViolation("Each entry of 'packages' must be an object", path));
                continue;
            }
            requireTextualField(pkg, "registryType", path, violations);
            requireTextualField(pkg, "identifier", path, violations);
            validateTransport(pkg, path, violations);
        }
    }

    private void validateRemotesField(JsonNode tree, Set<RuleViolation> violations) {
        if (!tree.has("remotes")) {
            return;
        }
        JsonNode remotes = tree.get("remotes");
        if (!remotes.isArray()) {
            violations.add(new RuleViolation("'remotes' field must be an array", "/remotes"));
            return;
        }
        for (int i = 0; i < remotes.size(); i++) {
            String path = "/remotes/" + i;
            JsonNode remote = remotes.get(i);
            if (!remote.isObject()) {
                violations.add(new RuleViolation("Each entry of 'remotes' must be an object", path));
                continue;
            }
            if (requireTextualField(remote, "url", path, violations)) {
                JsonValidationUtils.validateHttpUrl(remote.get("url").asText(), path + "/url", violations);
            }
            if (requireTextualField(remote, "type", path, violations)
                    && !TRANSPORT_TYPES.contains(remote.get("type").asText())) {
                violations.add(new RuleViolation(
                        "'type' must be one of " + TRANSPORT_TYPES, path + "/type"));
            }
        }
    }

    private void validateIconsField(JsonNode tree, Set<RuleViolation> violations) {
        if (!tree.has("icons")) {
            return;
        }
        JsonNode icons = tree.get("icons");
        if (!icons.isArray()) {
            violations.add(new RuleViolation("'icons' field must be an array", "/icons"));
            return;
        }
        for (int i = 0; i < icons.size(); i++) {
            String path = "/icons/" + i;
            JsonNode icon = icons.get(i);
            if (!icon.isObject()) {
                violations.add(new RuleViolation("Each entry of 'icons' must be an object", path));
                continue;
            }
            if (requireTextualField(icon, "src", path, violations)) {
                JsonValidationUtils.validateHttpUrl(icon.get("src").asText(), path + "/src", violations);
            }
        }
    }

    /**
     * The transport of a package is declared either as a bare string or as an object with a 'type'. Both
     * forms appear in the wild, so accept either and only check the discriminator value.
     */
    private void validateTransport(JsonNode pkg, String path, Set<RuleViolation> violations) {
        if (!pkg.has("transport")) {
            return;
        }
        JsonNode transport = pkg.get("transport");
        String type;
        if (transport.isTextual()) {
            type = transport.asText();
        } else if (transport.isObject() && transport.has("type") && transport.get("type").isTextual()) {
            type = transport.get("type").asText();
        } else {
            violations.add(new RuleViolation(
                    "'transport' must be a string or an object with a string 'type'", path + "/transport"));
            return;
        }
        if (!TRANSPORT_TYPES.contains(type)) {
            violations.add(new RuleViolation("'transport' must be one of " + TRANSPORT_TYPES,
                    path + "/transport"));
        }
    }

    private boolean requireTextualField(JsonNode node, String field, String path,
            Set<RuleViolation> violations) {
        if (!node.has(field)) {
            violations.add(new RuleViolation("Missing required field '" + field + "'", path + "/" + field));
            return false;
        }
        if (!node.get(field).isTextual()) {
            violations.add(new RuleViolation("'" + field + "' must be a string", path + "/" + field));
            return false;
        }
        return true;
    }

    @Override
    public void validateReferences(TypedContent content, List<ArtifactReference> references)
            throws RuleViolationException {
        // MCP server definitions don't support references
        if (references != null && !references.isEmpty()) {
            throw new RuleViolationException("MCP server definitions do not support references",
                    RuleType.INTEGRITY, "NONE", Collections.singleton(new RuleViolation(
                            "References are not supported for MCP server definitions", "")));
        }
    }
}
