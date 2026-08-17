package io.apicurio.registry.auth;

import jakarta.interceptor.InvocationContext;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies owner-check parameter indexing for {@link AuthorizedStyle} on single-parameter group operations.
 * Regression test for issue #7866 (getGroupById).
 */
class AbstractAccessControllerOwnerCheckTest {

    private static class TestableAccessController extends AbstractAccessController {

        @Override
        public boolean isAuthorized(InvocationContext context) {
            throw new UnsupportedOperationException();
        }

        boolean ownerCheck(InvocationContext context) {
            return isOwner(context);
        }
    }

    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.AdminOrOwner)
    public void groupAndArtifactMethod(String groupId) {
    }

    @Test
    void getGroupByIdUsesGroupOnlyInV3() throws Exception {
        Method method = io.apicurio.registry.rest.v3.impl.GroupsResourceImpl.class.getMethod("getGroupById",
                String.class);
        Authorized annotation = method.getAnnotation(Authorized.class);
        assertEquals(AuthorizedStyle.GroupOnly, annotation.style());
        assertEquals(AuthorizedLevel.Read, annotation.level());
    }

    @Test
    void getGroupByIdUsesGroupOnlyInV2() throws Exception {
        Method method = io.apicurio.registry.rest.v2.impl.GroupsResourceImpl.class.getMethod("getGroupById",
                String.class);
        Authorized annotation = method.getAnnotation(Authorized.class);
        assertEquals(AuthorizedStyle.GroupOnly, annotation.style());
        assertEquals(AuthorizedLevel.Read, annotation.level());
    }

    @Test
    void groupAndArtifactStyleRequiresArtifactParameter() throws Exception {
        TestableAccessController controller = new TestableAccessController();
        InvocationContext context = mock(InvocationContext.class);
        when(context.getMethod()).thenReturn(
                AbstractAccessControllerOwnerCheckTest.class.getMethod("groupAndArtifactMethod", String.class));
        when(context.getParameters()).thenReturn(new Object[] { "test-group" });

        assertThrows(ArrayIndexOutOfBoundsException.class, () -> controller.ownerCheck(context));
    }
}
