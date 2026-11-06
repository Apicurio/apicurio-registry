package io.apicurio.registry.content.refs.resolve;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.types.ContentTypes;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * A {@link SchemaSource} backed by a file on disk. Content is read lazily, once.
 */
public final class FileSchemaSource implements SchemaSource {

    private final Path file;
    private final String location;
    private final String artifactType;
    private final List<String> aliases = new ArrayList<>();
    private volatile TypedContent content;

    /**
     * Creates a file source with a detected artifact type.
     *
     * @param file the file
     * @param location the primary location (normalized via {@link SchemaSourceSet#normalize(String)})
     */
    public FileSchemaSource(Path file, String location) {
        this(file, location, null);
    }

    /**
     * Creates a file source.
     *
     * @param file the file
     * @param location the primary location (normalized via {@link SchemaSourceSet#normalize(String)})
     * @param artifactType the artifact type, or {@code null} to detect it
     */
    public FileSchemaSource(Path file, String location, String artifactType) {
        this.file = file;
        this.location = SchemaSourceSet.normalize(location);
        this.artifactType = artifactType;
        this.aliases.add(this.location);
    }

    /**
     * Returns the backing file.
     *
     * @return the file path
     */
    public Path file() {
        return file;
    }

    void addAlias(String alias) {
        String normalized = SchemaSourceSet.normalize(alias);
        if (!aliases.contains(normalized)) {
            aliases.add(normalized);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String location() {
        return location;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TypedContent content() {
        TypedContent result = content;
        if (result == null) {
            try {
                String raw = Files.readString(file, StandardCharsets.UTF_8);
                result = TypedContent.create(raw, contentTypeFor(file));
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to read schema file " + file, e);
            }
            content = result;
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<String> artifactType() {
        return Optional.ofNullable(artifactType);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> aliases() {
        return Collections.unmodifiableList(aliases);
    }

    /**
     * Determines the content type of a file from its extension.
     *
     * @param file the file
     * @return the content type; {@link ContentTypes#APPLICATION_JSON} when unknown
     */
    public static String contentTypeFor(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String ext = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        switch (ext) {
            case "yml":
            case "yaml":
                return ContentTypes.APPLICATION_YAML;
            case "proto":
                return ContentTypes.APPLICATION_PROTOBUF;
            case "graphql":
                return ContentTypes.APPLICATION_GRAPHQL;
            case "xml":
            case "xsd":
            case "wsdl":
                return ContentTypes.APPLICATION_XML;
            default:
                return ContentTypes.APPLICATION_JSON;
        }
    }

    @Override
    public String toString() {
        return "FileSchemaSource[" + location + "]";
    }
}
