package io.apicurio.registry.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.client.common.HttpAdapterType;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.noprofile.rest.v3.impexp.ExportLoader;
import io.apicurio.registry.tls.MtlsSdkClientJdkAdapterTest;
import io.apicurio.registry.tls.TlsSdkClientJdkAdapterTest;
import io.kiota.http.jdk.JDKRequestAdapter;
import io.kiota.http.vertx.VertXRequestAdapter;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Requires every SDK client built in the app module's test code to start from
 * AbstractResourceTestBase.boundedClientOptions, so a lost response fails after the read-idle bound instead of
 * hanging until the class timeout (#10424).
 *
 * <p>The rule flags the ways a test can get a client without that bound: options built with
 * RegistryClientOptions.create, by call or method reference; requestTimeout or customWebClient called outside
 * the helper, which loosen or drop the bound; a VertXRequestAdapter constructed directly; and the JDK adapter,
 * selected with HttpAdapterType.JDK or constructed directly. The JDK adapter ignores requestTimeout whatever
 * options it gets, so a test that needs it cannot be fixed with the helper and goes on the allowlist with its
 * reason instead.
 *
 * Any requestTimeout outside the helper is flagged, a tighter one included; a class that needs one goes on the
 * allowlist.
 *
 * <p>The rule sees method calls and constants, not values chosen at run time: httpAdapter(type) with a type
 * computed at run time, or registryUrl pointing the options at another server, still passes. It does not see
 * other modules, nor clients built by production code such as the SerDes resolver or the Maven plugin mojos.
 */
class TestClientTimeoutBoundaryTest {

    /** Classes allowed to build clients directly, each for a stated reason. Exemption is per class. */
    private static final Set<String> ALLOWED = Stream.of(
            // The JDK HTTP adapter ignores requestTimeout, so the helper would change nothing here.
            TlsSdkClientJdkAdapterTest.class,
            MtlsSdkClientJdkAdapterTest.class,
            // A main() load utility, not a test.
            ExportLoader.class,
            // Its nested fixtures break the rule on purpose, to prove that it fires.
            TestClientTimeoutBoundaryTest.class)
            .map(Class::getName)
            .collect(Collectors.toUnmodifiableSet());

    private static final DescribedPredicate<JavaClass> NOT_ALLOWED = DescribedPredicate.describe(
            "are not on the allowlist",
            clazz -> !ALLOWED.contains(clazz.getName().split("\\$")[0]));

    /** RegistryClientOptions methods that create options without the bound, or loosen or drop it. */
    private static final Set<String> UNBOUNDED_OPTIONS_METHODS = Set.of("create", "requestTimeout", "customWebClient");

    private static JavaClasses testClasses;
    private static JavaMethod helper;

    @BeforeAll
    static void importClasses() {
        testClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
                .importPackages("io.apicurio.registry");
        // Guards against a vacuous pass: the import must see the test classes this rule is about.
        assertTrue(testClasses.contain(AbstractResourceTestBase.class),
                "the ArchUnit import found no app test classes");
        // Matched by its full signature, so an overload with the same name is still checked.
        helper = testClasses.get(AbstractResourceTestBase.class)
                .getMethod("boundedClientOptions", String.class, Vertx.class);
    }

    @Test
    void testClientsStartFromTheBoundedOptions() {
        rule(NOT_ALLOWED).check(testClasses);
    }

    /** Each fixture builds an unbounded client one way; the rule must report it by name. */
    @ParameterizedTest
    @ValueSource(classes = { CreatesOptions.class, ReferencesCreate.class, LoosensTimeout.class,
            SuppliesWebClient.class, BuildsVertxAdapter.class, BuildsJdkAdapter.class, SelectsJdkAdapter.class })
    void ruleReportsEachWayToBuildAnUnboundedClient(Class<?> fixture) {
        JavaClasses fixtureClasses = new ClassFileImporter().importClasses(fixture);
        AssertionError error = assertThrows(AssertionError.class,
                () -> rule(DescribedPredicate.alwaysTrue()).check(fixtureClasses));
        assertTrue(error.getMessage().contains(fixture.getName() + ".build("), error.getMessage());
    }

    private static ArchRule rule(DescribedPredicate<? super JavaClass> checkedClasses) {
        DescribedPredicate<JavaAccess<?>> unboundedClient = DescribedPredicate.describe(
                "build options outside boundedClientOptions, override its bound, or use the JDK adapter",
                access -> !access.getOrigin().equals(helper) && (isUnboundedOptionsCall(access)
                        || isRawAdapter(access) || isJdkAdapterType(access)));
        return noClasses().that(checkedClasses)
                .should().accessTargetWhere(unboundedClient)
                .because("test clients must start from AbstractResourceTestBase.boundedClientOptions and keep "
                        + "its bound; a class that needs the JDK adapter, which ignores requestTimeout, or its own "
                        + "timeout belongs on the allowlist with its reason");
    }

    private static boolean isUnboundedOptionsCall(JavaAccess<?> access) {
        return access.getTargetOwner().isEquivalentTo(RegistryClientOptions.class)
                && UNBOUNDED_OPTIONS_METHODS.contains(access.getName());
    }

    /** Matched by exact class, so adapter test doubles that never open a socket are not flagged. */
    private static boolean isRawAdapter(JavaAccess<?> access) {
        JavaClass owner = access.getTargetOwner();
        return (owner.isEquivalentTo(VertXRequestAdapter.class) || owner.isEquivalentTo(JDKRequestAdapter.class))
                && "<init>".equals(access.getName());
    }

    private static boolean isJdkAdapterType(JavaAccess<?> access) {
        return access.getTargetOwner().isEquivalentTo(HttpAdapterType.class)
                && HttpAdapterType.JDK.name().equals(access.getName());
    }

    static class CreatesOptions {
        static Object build(Vertx vertx) {
            return RegistryClientOptions.create("http://localhost:1", vertx);
        }
    }

    static class ReferencesCreate {
        static Object build() {
            BiFunction<String, Vertx, RegistryClientOptions> create = RegistryClientOptions::create;
            return create;
        }
    }

    static class LoosensTimeout {
        static Object build(RegistryClientOptions options) {
            return options.requestTimeout(30_000, 600_000);
        }
    }

    static class SuppliesWebClient {
        static Object build(RegistryClientOptions options) {
            return options.customWebClient(null);
        }
    }

    static class BuildsVertxAdapter {
        static Object build(Vertx vertx) {
            return new VertXRequestAdapter(vertx);
        }
    }

    static class BuildsJdkAdapter {
        static Object build() {
            return new JDKRequestAdapter();
        }
    }

    static class SelectsJdkAdapter {
        static Object build() {
            return HttpAdapterType.JDK;
        }
    }
}
