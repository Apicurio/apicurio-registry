package io.apicurio.registry.rest.v3.impl;

import io.apicurio.registry.extensions.PromptRenderHandler;
import io.apicurio.registry.rest.MissingRequiredParameterException;
import io.apicurio.registry.rest.v3.beans.RenderPromptRequest;
import io.apicurio.registry.rest.v3.beans.RenderPromptResponse;
import io.apicurio.registry.rest.v3.beans.Variables;
import jakarta.enterprise.inject.Instance;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Verifies that the core render endpoint delegates to a {@link PromptRenderHandler} when one is
 * deployed and responds with 404 when none is.
 */
class GroupsResourceImplRenderTest {

    @Test
    @SuppressWarnings("unchecked")
    void testRenderDelegatesToHandler() {
        PromptRenderHandler handler = mock(PromptRenderHandler.class);
        Instance<PromptRenderHandler> instance = mock(Instance.class);
        when(instance.isResolvable()).thenReturn(true);
        when(instance.get()).thenReturn(handler);
        RenderPromptRequest request = newRequest();
        RenderPromptResponse expected = new RenderPromptResponse();
        when(handler.render("my-group", "my-prompt", "1", request)).thenReturn(expected);

        GroupsResourceImpl resource = new GroupsResourceImpl();
        resource.promptRenderHandler = instance;

        assertSame(expected, resource.renderPromptTemplate("my-group", "my-prompt", "1", request));
        verify(handler).render("my-group", "my-prompt", "1", request);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testRenderReturnsNotFoundWithoutHandler() {
        Instance<PromptRenderHandler> instance = mock(Instance.class);
        when(instance.isResolvable()).thenReturn(false);

        GroupsResourceImpl resource = new GroupsResourceImpl();
        resource.promptRenderHandler = instance;

        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> resource.renderPromptTemplate("my-group", "my-prompt", "1", newRequest()));
        assertEquals("Prompt rendering is not available", ex.getMessage());
        verify(instance).isResolvable();
    }

    @Test
    @SuppressWarnings("unchecked")
    void testRenderValidatesParametersBeforeLookingUpHandler() {
        Instance<PromptRenderHandler> instance = mock(Instance.class);

        GroupsResourceImpl resource = new GroupsResourceImpl();
        resource.promptRenderHandler = instance;

        MissingRequiredParameterException ex = assertThrows(MissingRequiredParameterException.class,
                () -> resource.renderPromptTemplate("my-group", "my-prompt", "1", new RenderPromptRequest()));
        assertEquals("variables", ex.getParameter());
        verifyNoInteractions(instance);
    }

    private static RenderPromptRequest newRequest() {
        Variables variables = new Variables();
        variables.setAdditionalProperty("user", "Alice");
        RenderPromptRequest request = new RenderPromptRequest();
        request.setVariables(variables);
        return request;
    }
}
