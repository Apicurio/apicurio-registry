package io.quarkus.test.common;

import static io.quarkus.test.common.PathTestHelper.getTestClassesLocation;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystem;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;

import org.jboss.jandex.Index;
import org.jboss.jandex.IndexReader;
import org.jboss.jandex.IndexWriter;
import org.jboss.jandex.Indexer;
import org.jboss.jandex.UnsupportedVersion;

import io.quarkus.fs.util.ZipUtils;

/**
 * Thread-safe and crash-resilient test class indexer overriding Quarkus 3.33.3.1's
 * io.quarkus.test.common.TestClassIndexer.
 *
 * Resolves intermittent "Not a jandex index" failures during parallel test execution (#10384):
 * - Serializes writes using an internal monitor lock.
 * - Writes to a temporary file in a sibling directory and atomically moves it into place.
 * - Catches IllegalArgumentException ("Not a jandex index") on read and recovers via in-memory indexing.
 */
public final class TestClassIndexer {

    public static final String TEST_CLASSES_IDX = "test-classes.idx";

    private static final Object LOCK = new Object();

    private TestClassIndexer() {
    }

    public static Index indexTestClasses(Class<?> testClass) {
        return indexTestClasses(getTestClassesLocation(testClass));
    }

    public static Index indexTestClasses(final Path testClassesLocation) {
        final Indexer indexer = new Indexer();
        try {
            if (Files.isDirectory(testClassesLocation)) {
                indexTestClassesDir(indexer, testClassesLocation);
            } else {
                try (FileSystem jarFs = ZipUtils.newFileSystem(testClassesLocation)) {
                    for (Path p : jarFs.getRootDirectories()) {
                        indexTestClassesDir(indexer, p);
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to index the test-classes/ directory.", e);
        }
        return indexer.complete();
    }

    public static void writeIndex(Index index, Class<?> testClass) {
        writeIndex(index, getTestClassesLocation(testClass), testClass);
    }

    public static void writeIndex(Index index, Path testClassLocation, Class<?> testClass) {
        synchronized (LOCK) {
            Path target = indexPath(testClassLocation);
            Path tmp = null;
            try {
                Path tmpDir = tempDirFor(testClassLocation);
                Files.createDirectories(tmpDir);
                tmp = Files.createTempFile(tmpDir, TEST_CLASSES_IDX + ".", ".tmp");
                try (OutputStream os = Files.newOutputStream(tmp)) {
                    new IndexWriter(os).write(index);
                }
                try {
                    Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                }
                tmp = null;
            } catch (IOException ignored) {
                // Recoverable at read site by recreating the index in memory
            } finally {
                if (tmp != null) {
                    try {
                        Files.deleteIfExists(tmp);
                    } catch (IOException ignore) {
                        // Best effort cleanup
                    }
                }
            }
        }
    }

    private static Path tempDirFor(Path testClassLocation) {
        Path parent = testClassLocation.getParent();
        return parent != null ? parent.resolve(".quarkus-test-classes-idx-tmp") : testClassLocation;
    }

    public static Index readIndex(Class<?> testClass) {
        return readIndex(getTestClassesLocation(testClass), testClass);
    }

    public static Index readIndex(Path testClassLocation, Class<?> testClass) {
        final Path path = indexPath(testClassLocation);
        if (Files.exists(path)) {
            try (FileInputStream fis = new FileInputStream(path.toFile())) {
                return new IndexReader(fis).read();
            } catch (UnsupportedVersion e) {
                throw new UnsupportedVersion("Can't read Jandex index from " + path + ": " + e.getMessage());
            } catch (IOException | IllegalArgumentException e) {
                // Be lenient since the error is recoverable: IllegalArgumentException covers
                // "Not a jandex index" when the file was left corrupt or torn by another process
                return testClassLocation != null ? indexTestClasses(testClassLocation) : indexTestClasses(testClass);
            }
        } else {
            return testClassLocation != null ? indexTestClasses(testClassLocation) : indexTestClasses(testClass);
        }
    }

    private static Path indexPath(Class<?> testClass) {
        return indexPath(PathTestHelper.getTestClassesLocation(testClass));
    }

    private static Path indexPath(Path testClassLocation) {
        return testClassLocation.resolve(TEST_CLASSES_IDX);
    }

    private static void indexTestClassesDir(Indexer indexer, final Path testClassesLocation) throws IOException {
        Files.walkFileTree(testClassesLocation, new FileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                    throws IOException {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!file.getFileName().toString().endsWith(".class")) {
                    return FileVisitResult.CONTINUE;
                }
                try (InputStream inputStream = Files.newInputStream(file, StandardOpenOption.READ)) {
                    indexer.index(inputStream);
                } catch (Exception e) {
                    // Ignore individual class indexing failures
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static void removeIndex(Class<?> requiredTestClass) {
        synchronized (LOCK) {
            try {
                Files.deleteIfExists(indexPath(requiredTestClass));
            } catch (IOException e) {
                throw new IllegalStateException("Unable to delete file index", e);
            }
        }
    }
}
