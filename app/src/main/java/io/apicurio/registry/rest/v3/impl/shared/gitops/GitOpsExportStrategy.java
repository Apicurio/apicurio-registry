package io.apicurio.registry.rest.v3.impl.shared.gitops;

import io.apicurio.registry.rest.v3.impl.shared.ExportFormatStrategy;
import io.apicurio.registry.utils.impexp.Entity;
import io.apicurio.registry.utils.impexp.v3.ContentEntity;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class GitOpsExportStrategy implements ExportFormatStrategy {

    private static final String SHARED_CONTENT_DIR = "content/";

    private final Logger log;
    private final GitOpsEntityCollector collector = new GitOpsEntityCollector();
    private final Map<Long, String> contentIdToPath = new HashMap<>();
    private ZipOutputStream zip;

    public GitOpsExportStrategy(Logger log) {
        this.log = log;
    }

    @Override
    public void init(ZipOutputStream zip) {
        this.zip = zip;
    }

    @Override
    public void handleEntity(Entity entity) throws IOException {
        if (entity instanceof ContentEntity ce) {
            writeContentImmediately(ce);
        }
        collector.collect(entity);
    }

    @Override
    public void finish() throws IOException {
        final List<String> warnings = collector.getWarnings();
        for (final String warning : warnings) {
            log.warn(warning);
        }
        final GitOpsZipWriter writer = new GitOpsZipWriter(zip, collector, contentIdToPath);
        writer.write();
        if (!warnings.isEmpty()) {
            writeWarningsFile(warnings);
        }
    }

    private void writeWarningsFile(List<String> warnings) throws IOException {
        final StringBuilder sb = new StringBuilder();
        for (final String warning : warnings) {
            sb.append(warning).append('\n');
        }
        zip.putNextEntry(new ZipEntry("WARNINGS.txt"));
        zip.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private void writeContentImmediately(ContentEntity ce) throws IOException {
        final String ext = GitOpsZipWriter.resolveExtension(ce.artifactType, ce.contentType);
        final String fileName = ce.contentId + ext;
        final String path = SHARED_CONTENT_DIR + fileName;
        contentIdToPath.put(ce.contentId, path);

        zip.putNextEntry(new ZipEntry(path));
        if (ce.contentBytes != null) {
            zip.write(ce.contentBytes);
        }
        zip.closeEntry();
    }
}
