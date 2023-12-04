package io.apicurio.registry.storage.impl.gitops.model;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.impl.gitops.ProcessingState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Optional;

import static lombok.AccessLevel.PRIVATE;


@Builder
@AllArgsConstructor(access = PRIVATE)
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class GitFile {

    private static final Logger log = LoggerFactory.getLogger(GitFile.class);

    @EqualsAndHashCode.Include
    private String path;

    private ContentHandle data;

    private Optional<Any> any;

    @Setter
    private boolean processed;

    public static GitFile create(ProcessingState state, String path, InputStream stream) {

        var data = ContentHandle.create(stream);

        return GitFile.builder()
                .path(FilenameUtils.normalize(path))
                .data(data)
                .any(Any.from(state, path, data))
                .build();
    }

    public boolean isType(Type type) {
        return any.map(a -> type == a.getType()).orElse(false);
    }

    @SuppressWarnings("unchecked")
    public <T> T getEntityUnchecked() {
        return (T) any.get().getEntity();
    }
}
