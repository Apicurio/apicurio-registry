package io.apicurio.registry.logging.audit;

import io.apicurio.registry.logging.audit.AuditedOperations.Mode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditEventModelTest {

    private static final Set<Type> ALLOWED_LEAF_TYPES = Set.of(String.class, int.class, AuditEvent.Outcome.class);

    /**
     * The event can only hold identifiers and enumerated values: every component, recursively, is a
     * string, an int, the outcome enum, a list of strings or a nested record of the event. There is no
     * Object, Map or Throwable component through which a body, a header or an exception could travel.
     */
    @Test
    void eventComponentsAreRestrictedToIdentifierTypes() {
        List<String> violations = new ArrayList<>();
        checkComponents(AuditEvent.class, violations);
        assertEquals(List.of(), violations);
    }

    private static void checkComponents(Class<?> record, List<String> violations) {
        for (RecordComponent component : record.getRecordComponents()) {
            Type type = component.getGenericType();
            if (ALLOWED_LEAF_TYPES.contains(type)) {
                continue;
            }
            if (type instanceof ParameterizedType p && p.getRawType() == List.class
                    && p.getActualTypeArguments()[0] == String.class) {
                continue;
            }
            if (type instanceof Class<?> c && c.isRecord() && c.getEnclosingClass() == AuditEvent.class) {
                checkComponents(c, violations);
                continue;
            }
            violations.add(record.getSimpleName() + "." + component.getName() + ": " + type.getTypeName());
        }
    }

    @Test
    void outcomeFollowsTheStatusCode() {
        assertEquals(AuditEvent.Outcome.SUCCESS, AuditEvent.Outcome.of(200));
        assertEquals(AuditEvent.Outcome.SUCCESS, AuditEvent.Outcome.of(204));
        assertEquals(AuditEvent.Outcome.DENIED, AuditEvent.Outcome.of(401));
        assertEquals(AuditEvent.Outcome.DENIED, AuditEvent.Outcome.of(403));
        assertEquals(AuditEvent.Outcome.FAILURE, AuditEvent.Outcome.of(400));
        assertEquals(AuditEvent.Outcome.FAILURE, AuditEvent.Outcome.of(404));
        assertEquals(AuditEvent.Outcome.FAILURE, AuditEvent.Outcome.of(409));
        assertEquals(AuditEvent.Outcome.FAILURE, AuditEvent.Outcome.of(500));
    }

    @Test
    void reasonIsTheStandardPhraseForErrorsOnly() {
        assertNull(AuditEventFilter.reason(200));
        assertNull(AuditEventFilter.reason(204));
        assertEquals("BadRequest", AuditEventFilter.reason(400));
        assertEquals("Forbidden", AuditEventFilter.reason(403));
        assertEquals("NotFound", AuditEventFilter.reason(404));
        assertEquals("Conflict", AuditEventFilter.reason(409));
        assertEquals("UnprocessableEntity", AuditEventFilter.reason(422));
        assertEquals("InternalServerError", AuditEventFilter.reason(500));
        assertNull(AuditEventFilter.reason(599));
    }

    @Test
    void mutationsAndDenialsAreEmittedButPlainReadsAreNot() {
        assertTrue(AuditedOperations.shouldEmit("POST", Mode.DEFAULT, 200));
        assertTrue(AuditedOperations.shouldEmit("put", Mode.DEFAULT, 200));
        assertTrue(AuditedOperations.shouldEmit("PATCH", Mode.DEFAULT, 500));
        assertTrue(AuditedOperations.shouldEmit("DELETE", Mode.DEFAULT, 404));
        assertFalse(AuditedOperations.shouldEmit("GET", Mode.DEFAULT, 200));
        assertFalse(AuditedOperations.shouldEmit("GET", Mode.DEFAULT, 404));
        assertTrue(AuditedOperations.shouldEmit("GET", Mode.DEFAULT, 401));
        assertTrue(AuditedOperations.shouldEmit("GET", Mode.DEFAULT, 403));
        assertFalse(AuditedOperations.shouldEmit("POST", Mode.EXEMPT, 200));
        assertFalse(AuditedOperations.shouldEmit("POST", Mode.EXEMPT, 400));
        assertTrue(AuditedOperations.shouldEmit("POST", Mode.EXEMPT, 403));
        assertTrue(AuditedOperations.shouldEmit("GET", Mode.ALWAYS, 200));
    }

    @Test
    void apiAndObjectAreDerivedFromThePathTemplate() {
        assertEquals("v3", AuditedOperations.apiOf("/apis/registry/v3/groups/{groupId}/artifacts"));
        assertEquals("v2", AuditedOperations.apiOf("/apis/registry/v2/admin/rules"));
        assertEquals("ccompat-v7", AuditedOperations.apiOf("/apis/ccompat/v7/subjects/{subject}"));
        assertEquals("ccompat-v8", AuditedOperations.apiOf("/apis/ccompat/v8/mode"));
        assertEquals("iceberg-v1", AuditedOperations.apiOf("/apis/iceberg/v1/{prefix}/namespaces"));
        assertEquals("mcp-registry-v0.1", AuditedOperations.apiOf("/apis/mcp-registry/v0.1/servers"));
        assertEquals("well-known", AuditedOperations.apiOf("/.well-known/ard/search"));

        assertEquals("rules", AuditedOperations.resourceOf("/apis/registry/v3/groups/{groupId}/rules/{ruleType}"));
        assertEquals("mode", AuditedOperations.resourceOf("/apis/ccompat/v7/mode/{subject}"));
        assertEquals(List.of("groupId", "artifactId", "versionExpression"), AuditedOperations.pathParamsOf(
                "/groups/{groupId}/artifacts/{artifactId}/versions/{versionExpression: .+}"));
    }
}
