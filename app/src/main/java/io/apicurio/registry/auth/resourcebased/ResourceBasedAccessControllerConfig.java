package io.apicurio.registry.auth.resourcebased;

import io.apicurio.common.apps.config.Info;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_AUTH;

@Singleton
public class ResourceBasedAccessControllerConfig {

    @ConfigProperty(name = "apicurio.auth.resource-based-authorization.enabled", defaultValue = "false")
    @Info(category = CATEGORY_AUTH, description = "Enable resource-based access control using Kroxylicious Authorizer", availableSince = "3.0.0", experimental = true)
    boolean enabled;

    @ConfigProperty(name = "apicurio.auth.resource-based-authorization.acl.file", defaultValue = "")
    @Info(category = CATEGORY_AUTH, description = "Path to ACL rules file for resource-based authorization", availableSince = "3.0.0", experimental = true)
    String aclFilePath;

    public boolean isEnabled() {
        return enabled;
    }

    public String getAclFilePath() {
        return aclFilePath;
    }
}
