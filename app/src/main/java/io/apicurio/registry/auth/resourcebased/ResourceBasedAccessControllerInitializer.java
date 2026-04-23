package io.apicurio.registry.auth.resourcebased;

import java.io.UncheckedIOException;
import java.nio.file.Path;

import io.kroxylicious.authorizer.provider.acl.AclAuthorizerConfig;
import io.kroxylicious.authorizer.provider.acl.AclAuthorizerService;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;

@Singleton
@Startup
public class ResourceBasedAccessControllerInitializer {

    @Inject
    Logger log;

    @Inject
    ResourceBasedAccessControllerConfig config;

    @Inject
    ResourceBasedAccessController controller;

    @PostConstruct
    void init() {
        if (!config.isEnabled()) {
            log.debug("Resource-based access control is disabled.");
            return;
        }

        String aclFile = config.getAclFilePath();
        if (aclFile == null || aclFile.isBlank()) {
            log.warn("Resource-based access control is enabled but no ACL file is configured. "
                    + "Set apicurio.auth.resource-based-authorization.acl.file to a valid path.");
            return;
        }

        log.info("Initializing resource-based access control from ACL file: {}", aclFile);
        try {
            AclAuthorizerService service = new AclAuthorizerService();
            service.initialize(new AclAuthorizerConfig(aclFile));
            controller.setAuthorizer(service.build());
            log.info("Resource-based access control initialized successfully.");
        } catch (UncheckedIOException e) {
            log.error("Failed to load ACL rules file: {}", aclFile, e);
            throw e;
        }
    }
}
