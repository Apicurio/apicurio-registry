package io.apicurio.registry.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.client.common.HttpAdapterType;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.kiota.http.jdk.JDKRequestAdapter;
import io.kiota.http.vertx.VertXRequestAdapter;
import io.vertx.core.Vertx;

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * The rule behind {@link TestClientTimeoutBoundaryTest}, shared through the app test-jar so that modules whose
 * tests extend AbstractResourceTestBase, such as agents, apply the same rule to their own test classes.
 */
public final class TestClientTimeoutRule {

    /** RegistryClientOptions methods that create options without the bound, or loosen or drop it. */
    private static final Set<String> UNBOUNDED_OPTIONS_METHODS = Set.of("create", "requestTimeout", "customWebClient");

    private TestClientTimeoutRule() {
    }

    /**
     * Returns AbstractResourceTestBase.boundedClientOptions, matched by its full signature so an overload with
     * the same name is still checked. Imported on its own, so callers need not import the app test classes.
     */
    public static JavaMethod helper() {
        return new ClassFileImporter().importClass(AbstractResourceTestBase.class)
                .getMethod("boundedClientOptions", String.class, Vertx.class);
    }

    /** Forbids the classes matching {@code checkedClasses} from building a client without the helper's bound. */
    public static ArchRule rule(DescribedPredicate<? super JavaClass> checkedClasses, JavaMethod helper) {
        DescribedPredicate<JavaAccess<?>> unboundedClient = DescribedPredicate.describe(
                "build options outside boundedClientOptions, override its bound, or use the JDK adapter",
                access -> !isSameMethod(access.getOrigin(), helper) && (isUnboundedOptionsCall(access)
                        || isRawAdapter(access) || isJdkAdapterType(access)));
        return noClasses().that(checkedClasses)
                .should().accessTargetWhere(unboundedClient)
                .because("test clients must start from AbstractResourceTestBase.boundedClientOptions and keep "
                        + "its bound; a class that needs the JDK adapter, which ignores requestTimeout, or its own "
                        + "timeout belongs on the allowlist with its reason");
    }

    /** Compared by full name, so a helper imported separately from the checked classes still matches. */
    private static boolean isSameMethod(Object origin, JavaMethod helper) {
        return origin instanceof JavaMethod method && method.getFullName().equals(helper.getFullName());
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
}
