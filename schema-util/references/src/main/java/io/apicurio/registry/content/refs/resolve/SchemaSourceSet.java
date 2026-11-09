package io.apicurio.registry.content.refs.resolve;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An ordered set of {@link SchemaSource}s, keyed by their primary location.
 */
public final class SchemaSourceSet {

    private static final Set<String> DEFAULT_EXTENSIONS = Set.of("json", "yml", "yaml", "xml", "xsd", "wsdl",
            "graphql", "avsc", "proto");

    /**
     * Accepts regular files with a known schema extension.
     */
    public static final Predicate<Path> DEFAULT_FILTER = path -> {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot >= 0 && DEFAULT_EXTENSIONS.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    };

    private final Map<String, SchemaSource> sources = new LinkedHashMap<>();

    private SchemaSourceSet() {
    }

    /**
     * Builds a set by walking directories. Each file's primary location is relative to the first root that
     * contains it; later roots containing the same file contribute aliases.
     *
     * @param roots the root directories, in priority order
     * @param filter selects the files to include
     * @return the source set
     */
    public static SchemaSourceSet fromDirectories(List<Path> roots, Predicate<Path> filter) {
        Map<Path, FileSchemaSource> byFile = new LinkedHashMap<>();
        for (Path root : roots) {
            Path absRoot = root.toAbsolutePath().normalize();
            List<Path> files;
            try (Stream<Path> walk = Files.walk(absRoot)) {
                files = walk.filter(Files::isRegularFile).filter(filter).sorted().collect(Collectors.toList());
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to walk directory " + root, e);
            }
            for (Path file : files) {
                Path absFile = file.toAbsolutePath().normalize();
                String relative = absRoot.relativize(absFile).toString();
                FileSchemaSource existing = byFile.get(absFile);
                if (existing == null) {
                    byFile.put(absFile, new FileSchemaSource(absFile, relative));
                } else {
                    existing.addAlias(relative);
                }
            }
        }
        SchemaSourceSet set = new SchemaSourceSet();
        byFile.values().forEach(set::add);
        return set;
    }

    /**
     * Builds a set from explicit sources.
     *
     * @param sources the sources
     * @return the source set
     * @throws IllegalArgumentException on a duplicate location
     */
    public static SchemaSourceSet of(SchemaSource... sources) {
        SchemaSourceSet set = new SchemaSourceSet();
        for (SchemaSource source : sources) {
            set.add(source);
        }
        return set;
    }

    /**
     * Adds a source.
     *
     * @param source the source
     * @return this set
     * @throws IllegalArgumentException if a source with the same location already exists
     */
    public SchemaSourceSet add(SchemaSource source) {
        String location = source.location();
        if (sources.containsKey(location)) {
            throw new IllegalArgumentException("Duplicate schema source location: " + location);
        }
        sources.put(location, source);
        return this;
    }

    /**
     * Returns all sources in insertion order.
     *
     * @return an immutable list of sources
     */
    public List<SchemaSource> sources() {
        return List.copyOf(sources.values());
    }

    /**
     * Looks up a source by its primary location.
     *
     * @param location the location (normalized before lookup)
     * @return the source, if present
     */
    public Optional<SchemaSource> byLocation(String location) {
        return Optional.ofNullable(sources.get(normalize(location)));
    }

    /**
     * Returns the primary location of the file-backed source for the given file path.
     *
     * @param file the file path (made absolute and normalized before comparison)
     * @return the primary location of the matching source
     * @throws IllegalArgumentException if no file-backed source exists for the path
     */
    public String locationOf(Path file) {
        Path abs = file.toAbsolutePath().normalize();
        return sources.values().stream()
                .filter(s -> s instanceof FileSchemaSource && ((FileSchemaSource) s).file().equals(abs))
                .map(SchemaSource::location)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No schema source for file: " + file));
    }

    /**
     * Normalizes a location to a POSIX path: backslashes become slashes, "." and ".." segments are
     * resolved, and any leading "./" or "/" is stripped. This is a pure string transformation producing a
     * lookup key; the result is never resolved against the filesystem.
     *
     * @param location the raw location
     * @return the normalized location
     */
    public static String normalize(String location) {
        String path = location.replace('\\', '/');
        Deque<String> segments = new ArrayDeque<>();
        for (String segment : path.split("/")) {
            if (segment.isEmpty() || ".".equals(segment)) {
                continue;
            }
            if ("..".equals(segment) && !segments.isEmpty() && !"..".equals(segments.peekLast())) {
                segments.removeLast();
            } else {
                segments.addLast(segment);
            }
        }
        return String.join("/", new ArrayList<>(segments));
    }
}
