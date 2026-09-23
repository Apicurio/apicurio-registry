package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.extensions.ArtifactVersionWriteHook;
import io.apicurio.registry.extensions.PreparedContent;
import io.apicurio.registry.extensions.VersionWriteContext;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.rules.violation.RuleViolation;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.types.RuleType;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Test-only {@link ArtifactVersionWriteHook} used by {@link ArtifactVersionWriteHookTest}.
 * <p>
 * The bean is active in every Quarkus test, so it ignores all writes except those to groups starting
 * with {@link #GROUP_PREFIX}. Events are recorded as {@code stage:operation:artifactId} so each test can
 * filter on its own artifact.
 * </p>
 */
@ApplicationScoped
public class RecordingWriteHook implements ArtifactVersionWriteHook {

    static final String GROUP_PREFIX = "write-hook-test-";
    static final String REWRITE_MARKER = "rewrite-me";
    static final String REWRITTEN_MARKER = "rewritten-by-hook";
    static final String REJECT_MARKER = "reject-me";

    private final Queue<String> events = new ConcurrentLinkedQueue<>();

    @Override
    public PreparedContent prepareContent(VersionWriteContext context, TypedContent content) {
        if (!applies(context)) {
            return null;
        }
        events.add("prepare:" + context.getOperation() + ":" + context.getArtifactId());
        String text = content.getContent().content();
        if (!text.contains(REWRITE_MARKER)) {
            return null;
        }
        return new PreparedContent(ContentHandle.create(text.replace(REWRITE_MARKER, REWRITTEN_MARKER)),
                content.getContentType(), List.<ArtifactReferenceDto> of());
    }

    @Override
    public Runnable beforePublish(VersionWriteContext context, TypedContent content) {
        if (!applies(context)) {
            return null;
        }
        if (content.getContent().content().contains(REJECT_MARKER)) {
            throw new RuleViolationException("Rejected by test write hook", RuleType.VALIDITY,
                    ValidityLevel.FULL.name(), Set.of(new RuleViolation("contains " + REJECT_MARKER, "/description")));
        }
        events.add("before:" + context.getOperation() + ":" + context.getArtifactId());
        return () -> events.add("after:" + context.getOperation() + ":" + context.getArtifactId());
    }

    List<String> eventsFor(String artifactId) {
        return events.stream().filter(e -> e.endsWith(":" + artifactId)).toList();
    }

    private static boolean applies(VersionWriteContext context) {
        return context.getGroupId() != null && context.getGroupId().startsWith(GROUP_PREFIX);
    }
}
