package io.apicurio.registry.maven;

import com.microsoft.kiota.RequestAdapter;
import com.microsoft.kiota.RequestInformation;
import com.microsoft.kiota.serialization.Parsable;
import com.microsoft.kiota.serialization.ParsableFactory;
import com.microsoft.kiota.serialization.SerializationWriterFactory;
import com.microsoft.kiota.serialization.ValuedEnumParser;
import com.microsoft.kiota.store.BackingStoreFactory;
import io.apicurio.registry.rest.client.RegistryClient;
import io.kiota.serialization.json.JsonSerializationWriterFactory;
import io.vertx.core.Vertx;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An interrupt while registering one artifact must stop the whole mojo, restore the thread's
 * interrupt flag, and not go on to register the remaining artifacts.
 */
public class RegisterRegistryMojoInterruptTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void clearInterruptFlag() {
        // Never leak an interrupted thread into other tests.
        Thread.interrupted();
    }

    @Test
    void interruptStopsRegistrationAndRestoresInterruptFlag() throws Exception {
        InterruptingAdapter adapter = new InterruptingAdapter();
        RegisterRegistryMojo mojo = new RegisterRegistryMojo() {
            @Override
            protected Vertx createVertx() {
                return null;
            }

            @Override
            protected RegistryClient createClient(Vertx vertx) {
                return new RegistryClient(adapter);
            }
        };
        mojo.setArtifacts(List.of(artifact("first"), artifact("second")));

        MojoExecutionException ex = assertThrows(MojoExecutionException.class, mojo::executeInternal);

        assertEquals("Interrupted while registering artifact [test-group] / [first]", ex.getMessage());
        assertEquals(InterruptedException.class, ex.getCause().getClass());
        assertTrue(Thread.currentThread().isInterrupted(), "interrupt flag must be restored");
        assertEquals(1, adapter.requests.get(), "the second artifact must not be attempted");
    }

    private RegisterArtifact artifact(String artifactId) throws Exception {
        Path file = tempDir.resolve(artifactId + ".avsc");
        Files.writeString(file, "{\"type\":\"string\"}");
        RegisterArtifact artifact = new RegisterArtifact();
        artifact.setGroupId("test-group");
        artifact.setArtifactId(artifactId);
        artifact.setArtifactType("AVRO");
        artifact.setFile(file.toFile());
        return artifact;
    }

    /**
     * Request adapter that fails every request with an InterruptedException, as a blocking client call
     * does when the build thread is interrupted. Kiota's send methods don't declare it, so it is thrown
     * unchecked-style, exactly as it would propagate from the real adapter.
     */
    private static final class InterruptingAdapter implements RequestAdapter {
        final AtomicInteger requests = new AtomicInteger();

        @SuppressWarnings("unchecked")
        private static <T extends Throwable> RuntimeException sneakyThrow(Throwable t) throws T {
            throw (T) t;
        }

        private RuntimeException interrupted() {
            requests.incrementAndGet();
            return sneakyThrow(new InterruptedException("simulated interrupt"));
        }

        @Override
        public void enableBackingStore(BackingStoreFactory backingStoreFactory) {
        }

        @Override
        public SerializationWriterFactory getSerializationWriterFactory() {
            return new JsonSerializationWriterFactory();
        }

        @Override
        public <T extends Parsable> T send(RequestInformation request,
                HashMap<String, ParsableFactory<? extends Parsable>> errorMappings, ParsableFactory<T> factory) {
            throw interrupted();
        }

        @Override
        public <T extends Parsable> List<T> sendCollection(RequestInformation request,
                HashMap<String, ParsableFactory<? extends Parsable>> errorMappings, ParsableFactory<T> factory) {
            throw interrupted();
        }

        @Override
        public <T> T sendPrimitive(RequestInformation request,
                HashMap<String, ParsableFactory<? extends Parsable>> errorMappings, Class<T> targetClass) {
            throw interrupted();
        }

        @Override
        public <T> List<T> sendPrimitiveCollection(RequestInformation request,
                HashMap<String, ParsableFactory<? extends Parsable>> errorMappings, Class<T> targetClass) {
            throw interrupted();
        }

        @Override
        public <T extends Enum<T>> T sendEnum(RequestInformation request,
                HashMap<String, ParsableFactory<? extends Parsable>> errorMappings, ValuedEnumParser<T> parser) {
            throw interrupted();
        }

        @Override
        public <T extends Enum<T>> List<T> sendEnumCollection(RequestInformation request,
                HashMap<String, ParsableFactory<? extends Parsable>> errorMappings, ValuedEnumParser<T> parser) {
            throw interrupted();
        }

        @Override
        public void setBaseUrl(String baseUrl) {
        }

        @Override
        public String getBaseUrl() {
            return "http://localhost";
        }

        @Override
        public <T> T convertToNativeRequest(RequestInformation request) {
            throw new UnsupportedOperationException();
        }
    }
}
