package io.apicurio.registry.support;

import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
public interface SupportAiService {

    String chat(@UserMessage String message);
}
