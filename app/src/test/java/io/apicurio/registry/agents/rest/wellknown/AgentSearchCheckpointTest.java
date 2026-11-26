package io.apicurio.registry.agents.rest.wellknown;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.agents.noprofile.rest.a2a.ExperimentalFeaturesEnabledProfile;
import io.apicurio.registry.extensions.AgentSearchHandler;
import io.apicurio.registry.federation.SearchDeadlineExceededException;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The checkpoint of the agent search, which is how a federated search stops the search of this
 * registry once its deadline has passed.
 */
@QuarkusTest
@TestProfile(ExperimentalFeaturesEnabledProfile.class)
public class AgentSearchCheckpointTest extends AbstractResourceTestBase {

    private static final String CARD = """
            {
                "name": "TestAgent",
                "description": "A test AI agent",
                "version": "1.0.0",
                "supportedInterfaces": [
                    { "url": "https://example.com/agent", "protocolBinding": "http+json", "protocolVersion": "1.0" }
                ],
                "capabilities": { "streaming": false, "pushNotifications": false },
                "skills": [ { "id": "test-skill", "name": "Test Skill", "description": "A test skill", "tags": ["t"] } ],
                "defaultInputModes": ["text"],
                "defaultOutputModes": ["text"]
            }
            """;

    @Inject
    AgentSearchHandler agentSearch;

    // The search is called here without an HTTP request, so the values it derives from one are given.
    @InjectMock
    WellKnownRequestSupport requestSupport;

    @BeforeEach
    void stubRequestValues() {
        Mockito.when(requestSupport.getBaseUrl()).thenReturn("http://localhost:8080");
        Mockito.when(requestSupport.resolvePublisherDomain()).thenReturn("localhost:8080");
    }

    private String threeCards() throws Exception {
        String skill = "checkpoint-" + UUID.randomUUID();
        String group = TestUtils.generateGroupId();
        for (int i = 0; i < 3; i++) {
            createArtifact(group, "card-" + i, ArtifactType.AGENT_CARD, CARD.replace("test-skill", skill),
                    ContentTypes.APPLICATION_JSON, null);
        }
        return skill;
    }

    @Test
    void theCheckpointRunsForEveryCandidateAndTheSearchStillCompletes() throws Exception {
        String skill = threeCards();
        AtomicInteger runs = new AtomicInteger();

        var results = agentSearch.searchAgents(null, List.of(skill), null, null, null, 0, 10, false,
                runs::incrementAndGet);

        assertEquals(3, results.getAgents().size());
        assertTrue(runs.get() >= 3, "The checkpoint ran " + runs.get() + " times.");
    }

    @Test
    void aCheckpointThatThrowsStopsTheSearch() throws Exception {
        String skill = threeCards();
        AtomicInteger runs = new AtomicInteger();

        assertThrows(SearchDeadlineExceededException.class, () -> agentSearch.searchAgents(null, List.of(skill),
                null, null, null, 0, 10, false, () -> {
                    if (runs.incrementAndGet() == 2) {
                        throw new SearchDeadlineExceededException(1);
                    }
                }));

        assertEquals(2, runs.get(), "The search should stop at the checkpoint that threw.");
    }
}
