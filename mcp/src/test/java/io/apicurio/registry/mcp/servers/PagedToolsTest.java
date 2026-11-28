package io.apicurio.registry.mcp.servers;

import io.apicurio.registry.mcp.Descriptions;
import io.quarkiverse.mcp.server.ToolArg;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Every paged list and search tool offers the optional {@code pageToken} and {@code pageSize}
 * arguments as its last two arguments (#10641).
 */
class PagedToolsTest {

    private static final Map<Class<?>, List<String>> PAGED_TOOLS = Map.of(
            GroupsMCPServer.class, List.of("list_groups", "search_groups"),
            ArtifactsMCPServer.class, List.of("list_artifacts", "search_artifacts"),
            VersionsMCPServer.class, List.of("list_versions", "search_versions"),
            AgentsMCPServer.class, List.of("discover_agents", "search_mcp_tools"));

    @Test
    void pagedToolsTakeOptionalPageTokenAndPageSize() {
        PAGED_TOOLS.forEach((server, tools) -> tools.forEach(tool -> {
            Parameter[] params = tool(server, tool).getParameters();
            Parameter token = params[params.length - 2];
            Parameter size = params[params.length - 1];

            assertEquals("pageToken", token.getName(), tool);
            assertEquals(String.class, token.getType(), tool);
            assertFalse(token.getAnnotation(ToolArg.class).required(), tool + " pageToken is optional");
            assertEquals(Descriptions.PAGE_TOKEN, token.getAnnotation(ToolArg.class).description(), tool);

            assertEquals("pageSize", size.getName(), tool);
            assertEquals(Integer.class, size.getType(), tool);
            assertFalse(size.getAnnotation(ToolArg.class).required(), tool + " pageSize is optional");
            assertEquals(Descriptions.PAGE_SIZE, size.getAnnotation(ToolArg.class).description(), tool);
        }));
    }

    private static Method tool(Class<?> server, String name) {
        return Arrays.stream(server.getDeclaredMethods())
                .filter(m -> m.getName().equals(name))
                .findFirst().orElseThrow(() -> new AssertionError(server.getSimpleName() + " has no tool " + name));
    }
}
