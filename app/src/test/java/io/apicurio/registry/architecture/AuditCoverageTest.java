package io.apicurio.registry.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.apicurio.registry.logging.audit.AuditAlways;
import io.apicurio.registry.logging.audit.AuditEventFilter;
import io.apicurio.registry.logging.audit.AuditExempt;
import io.apicurio.registry.logging.audit.AuditedOperations;
import io.apicurio.registry.logging.audit.AuditedOperations.Mode;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.ext.Provider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Enumerates every JAX-RS endpoint of the registry from the class files, the same way
 * {@link AgentsBoundaryTest} does, and checks that each one using a mutating verb (POST, PUT, PATCH,
 * DELETE) is audited by {@link AuditEventFilter} unless its implementation is marked {@link AuditExempt}.
 * The per-API endpoint counts and the exemption list are pinned, so adding, removing or exempting an
 * endpoint is a deliberate change to this test.
 */
class AuditCoverageTest {

    /**
     * Mutating JAX-RS methods per API. A method is one Java method on a JAX-RS interface or class, so an
     * OpenAPI operation that accepts two media types through two methods counts twice.
     */
    private static final Map<String, Integer> EXPECTED_MUTATING_ENDPOINTS = new TreeMap<>(Map.of(
            "v3", 68,
            "v2", 38,
            "ccompat-v7", 21,
            "ccompat-v8", 21,
            "iceberg-v1", 11,
            "mcp-registry-v0.1", 5,
            "well-known", 2));

    /**
     * Endpoints that use a mutating verb but change nothing. Their reasons are on the
     * {@link AuditExempt} annotations.
     */
    private static final Set<String> EXPECTED_EXEMPT = new TreeSet<>(Set.of(
            "ccompat-v7 POST /apis/ccompat/v7/compatibility/subjects/{subject}/versions checkAllCompatibility",
            "ccompat-v7 POST /apis/ccompat/v7/compatibility/subjects/{subject}/versions/{version} checkCompatibility",
            "ccompat-v7 POST /apis/ccompat/v7/subjects/{subject} lookupSchemaByContent",
            "ccompat-v7 POST /apis/ccompat/v7/subjects/{subject}/versions/{version} lookupSchemaByVersion",
            "ccompat-v8 POST /apis/ccompat/v8/compatibility/subjects/{subject}/versions checkAllCompatibility",
            "ccompat-v8 POST /apis/ccompat/v8/compatibility/subjects/{subject}/versions/{version} checkCompatibility",
            "ccompat-v8 POST /apis/ccompat/v8/subjects/{subject} lookupSchemaByContent",
            "ccompat-v8 POST /apis/ccompat/v8/subjects/{subject}/versions/{version} lookupSchemaByVersion",
            "v2 POST /apis/registry/v2/groups/{groupId}/artifacts/{artifactId}/meta getArtifactVersionMetaDataByContent",
            "v2 POST /apis/registry/v2/search/artifacts searchArtifactsByContent",
            "v2 PUT /apis/registry/v2/groups/{groupId}/artifacts/{artifactId}/test testUpdateArtifact",
            "v3 POST /apis/registry/v3/content/canonicalize canonicalizeContent",
            "v3 POST /apis/registry/v3/content/references detectContentReferences",
            "v3 POST /apis/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/{versionExpression}/contract/execute executeContractRules",
            "v3 POST /apis/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/{versionExpression}/render renderPromptTemplate",
            "v3 POST /apis/registry/v3/search/artifacts searchArtifactsByContent",
            "v3 POST /apis/registry/v3/search/versions searchVersionsByContent",
            "v3 POST /apis/registry/v3/well-known/ard/explore ardExplore",
            "v3 POST /apis/registry/v3/well-known/ard/search ardSearch",
            "well-known POST /.well-known/ard/explore ardExplore",
            "well-known POST /.well-known/ard/search ardSearch"));

    /**
     * JAX-RS interfaces that are generated into the registry but implemented elsewhere (the webhook
     * contract a custom artifact type's external service implements), so the registry serves none of
     * their endpoints.
     */
    private static final Set<String> EXPECTED_UNIMPLEMENTED = new TreeSet<>(Set.of(
            "io.apicurio.registry.types.webhooks.CompatibilityCheckerResource",
            "io.apicurio.registry.types.webhooks.ContentAccepterResource",
            "io.apicurio.registry.types.webhooks.ContentCanonicalizerResource",
            "io.apicurio.registry.types.webhooks.ContentDereferencerResource",
            "io.apicurio.registry.types.webhooks.ContentValidatorResource",
            "io.apicurio.registry.types.webhooks.ReferenceFinderResource"));

    private record Endpoint(String api, String verb, String template, String operation, Method implementation) {

        Mode mode() {
            return AuditedOperations.modeOf(implementation);
        }

        @Override
        public String toString() {
            return api + " " + verb + " " + template + " " + operation;
        }
    }

    private static final List<Endpoint> MUTATING = new ArrayList<>();
    private static final Set<String> UNIMPLEMENTED = new TreeSet<>();
    /** Every endpoint implementation method, whatever its verb, with that verb. */
    private static final Map<Method, String> ENDPOINT_VERBS = new HashMap<>();
    private static JavaClasses classes;

    @BeforeAll
    static void enumerateEndpoints() throws Exception {
        // Static so @BeforeAll can fill them; cleared so a rerun in the same JVM does not double the counts.
        MUTATING.clear();
        UNIMPLEMENTED.clear();
        ENDPOINT_VERBS.clear();
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("io.apicurio.registry");
        for (JavaClass resource : classes) {
            if (!resource.isAnnotatedWith(Path.class)) {
                continue;
            }
            Class<?> type = resource.reflect();
            List<Class<?>> implementations = implementationsOf(resource);
            for (Method method : type.getDeclaredMethods()) {
                String verb = httpMethodOf(method);
                if (verb == null) {
                    continue;
                }
                if (implementations.isEmpty()) {
                    UNIMPLEMENTED.add(type.getName());
                    continue;
                }
                assertEquals(1, implementations.size(),
                        "expected one implementation of " + type.getName() + ", found " + implementations);
                Method implementation = implementations.get(0).getMethod(method.getName(),
                        method.getParameterTypes());
                ENDPOINT_VERBS.put(implementation, verb);
                if (AuditedOperations.MUTATING_VERBS.contains(verb)) {
                    String template = AuditedOperations.pathTemplateOf(type, method);
                    MUTATING.add(new Endpoint(AuditedOperations.apiOf(template), verb, template,
                            method.getName(), implementation));
                }
            }
        }
        assertTrue(MUTATING.size() > 100, "the class import found too few endpoints: " + MUTATING.size());
    }

    private static List<Class<?>> implementationsOf(JavaClass resource) {
        if (!resource.isInterface()) {
            return List.of(resource.reflect());
        }
        return resource.getAllSubclasses().stream()
                .filter(c -> !c.isInterface() && !c.getModifiers().contains(JavaModifier.ABSTRACT))
                .<Class<?>> map(JavaClass::reflect)
                .toList();
    }

    /**
     * Applies the filter's own rule to every mutating endpoint. It turns red when that rule stops auditing
     * a mutating verb; a new or newly exempt endpoint is caught by the two pinning tests below.
     */
    @Test
    void everyMutatingEndpointIsAuditedUnlessExempt() {
        List<String> unaudited = MUTATING.stream()
                .filter(e -> e.mode() != Mode.EXEMPT)
                .filter(e -> !AuditedOperations.shouldEmit(e.verb(), e.mode(), 200))
                .map(Endpoint::toString)
                .toList();
        assertEquals(List.of(), unaudited, "mutating endpoints that would produce no audit event");
    }

    @Test
    void exemptionsArePinned() {
        Set<String> exempt = MUTATING.stream()
                .filter(e -> e.mode() == Mode.EXEMPT)
                .map(Endpoint::toString)
                .collect(Collectors.toCollection(TreeSet::new));
        assertEquals(EXPECTED_EXEMPT, exempt, "exempt endpoints changed");
    }

    @Test
    void endpointCountsArePinned() {
        Map<String, Integer> counts = new TreeMap<>();
        MUTATING.forEach(e -> counts.merge(e.api(), 1, Integer::sum));
        String table = MUTATING.stream().map(Endpoint::toString).sorted().collect(Collectors.joining("\n"));
        assertEquals(EXPECTED_MUTATING_ENDPOINTS, counts,
                "mutating endpoints per API changed; classify the new or removed endpoints. Current list:\n"
                        + table);
        assertEquals(EXPECTED_UNIMPLEMENTED, UNIMPLEMENTED, "JAX-RS interfaces without an implementation");
    }

    @Test
    void auditAnnotationsSitOnEndpointsWithAReason() {
        List<String> misplaced = new ArrayList<>();
        for (JavaClass javaClass : classes) {
            for (JavaMethod javaMethod : javaClass.getMethods()) {
                if (!javaMethod.isAnnotatedWith(AuditExempt.class) && !javaMethod.isAnnotatedWith(AuditAlways.class)) {
                    continue;
                }
                Method method = javaMethod.reflect();
                AuditExempt exempt = method.getAnnotation(AuditExempt.class);
                AuditAlways always = method.getAnnotation(AuditAlways.class);
                String verb = ENDPOINT_VERBS.get(method);
                if (verb == null
                        || (exempt != null && (exempt.reason().isBlank()
                                || !AuditedOperations.MUTATING_VERBS.contains(verb)))
                        || (always != null && (always.reason().isBlank() || !HttpMethod.GET.equals(verb)))) {
                    misplaced.add(method.toString());
                }
            }
        }
        assertEquals(List.of(), misplaced,
                "@AuditExempt needs a reason and a mutating endpoint, @AuditAlways a reason and a GET endpoint");
    }

    @Test
    void filterIsRegistered() {
        assertTrue(AuditEventFilter.class.isAnnotationPresent(Provider.class),
                "without @Provider the filter, and so every audit event, is gone");
    }

    /**
     * The HTTP verb of a JAX-RS resource method, from its annotation that is meta-annotated with
     * {@link HttpMethod}, or null for a method that is not an endpoint.
     */
    private static String httpMethodOf(Method method) {
        for (Annotation annotation : method.getAnnotations()) {
            HttpMethod httpMethod = annotation.annotationType().getAnnotation(HttpMethod.class);
            if (httpMethod != null) {
                return httpMethod.value();
            }
        }
        return null;
    }
}
